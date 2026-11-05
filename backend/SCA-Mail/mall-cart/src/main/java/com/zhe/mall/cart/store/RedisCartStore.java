package com.zhe.mall.cart.store;

import com.zhe.mall.common.cache.MallCacheKeys;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Redis 购物车存储。
 *
 * <p>结构：{@code mall:cart:{userId}} 是一个 Hash，每个 SKU 占两个 field：
 * {@code qty:{skuId}} 与 {@code sel:{skuId}}。这样 Lua 脚本可以直接读写数量，
 * 不需要在 Redis 里解析 JSON；同时列表读取只需一次 HGETALL。
 *
 * <p>加购与改数量都走 Lua：并发请求下"读旧值 → 加一 → 写回"不会互相覆盖，
 * 数量上限与库存上限也在同一个原子操作里判定。
 */
@Component
public class RedisCartStore implements CartStore {

    private static final String QTY_PREFIX = "qty:";

    private static final String SEL_PREFIX = "sel:";

    private static final String AT_PREFIX = "at:";

    private static final String ADD_SCRIPT = """
            local key = KEYS[1]
            local sku = ARGV[1]
            local add = tonumber(ARGV[2])
            local maxQty = tonumber(ARGV[3])
            local stock = tonumber(ARGV[4])
            local maxItems = tonumber(ARGV[5])
            local ttl = tonumber(ARGV[6])
            local qtyField = 'qty:' .. sku
            local current = tonumber(redis.call('HGET', key, qtyField) or '0')
            local nextQty = current + add
            if nextQty > maxQty then
                return -1
            end
            if nextQty > stock then
                return -2
            end
            if current == 0 then
                -- 每行固定 qty / sel / at 三个 field；用整数除法避免把 sel/at 误算成商品种类
                local rows = math.floor(redis.call('HLEN', key) / 3)
                if rows >= maxItems then
                    return -3
                end
            end
            redis.call('HSET', key, qtyField, nextQty)
            if redis.call('HEXISTS', key, 'sel:' .. sku) == 0 then
                redis.call('HSET', key, 'sel:' .. sku, '1')
            end
            if redis.call('HEXISTS', key, 'at:' .. sku) == 0 then
                redis.call('HSET', key, 'at:' .. sku, ARGV[7])
            end
            redis.call('EXPIRE', key, ttl)
            return nextQty
            """;

    private static final String UPDATE_SCRIPT = """
            local key = KEYS[1]
            local sku = ARGV[1]
            local qty = tonumber(ARGV[2])
            local maxQty = tonumber(ARGV[3])
            local stock = tonumber(ARGV[4])
            local ttl = tonumber(ARGV[5])
            local qtyField = 'qty:' .. sku
            if qty <= 0 then
                local removed = redis.call('HDEL', key, qtyField, 'sel:' .. sku, 'at:' .. sku)
                redis.call('EXPIRE', key, ttl)
                if removed == 0 then
                    return -4
                end
                return 0
            end
            if qty > maxQty then
                return -1
            end
            if qty > stock then
                return -2
            end
            if redis.call('HEXISTS', key, qtyField) == 0 then
                return -4
            end
            redis.call('HSET', key, qtyField, qty)
            redis.call('EXPIRE', key, ttl)
            return qty
            """;

    private static final String SELECT_SCRIPT = """
            local key = KEYS[1]
            local selected = ARGV[1]
            local ttl = tonumber(ARGV[2])
            local changed = 0
            for i = 3, #ARGV do
                local sku = ARGV[i]
                if redis.call('HEXISTS', key, 'qty:' .. sku) == 1 then
                    local old = redis.call('HGET', key, 'sel:' .. sku)
                    if old ~= selected then
                        redis.call('HSET', key, 'sel:' .. sku, selected)
                        changed = changed + 1
                    end
                end
            end
            if changed > 0 then
                redis.call('EXPIRE', key, ttl)
            end
            return changed
            """;

    private static final DefaultRedisScript<Long> ADD = script(ADD_SCRIPT);

    private static final DefaultRedisScript<Long> UPDATE = script(UPDATE_SCRIPT);

    private static final DefaultRedisScript<Long> SELECT = script(SELECT_SCRIPT);

    private final StringRedisTemplate redis;

    public RedisCartStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public int add(Long userId, Long skuId, int quantity, int maxQuantity, int stockLimit,
                   int maxItems, Duration ttl) {
        Long result = redis.execute(ADD, List.of(key(userId)),
                String.valueOf(skuId), String.valueOf(quantity), String.valueOf(maxQuantity),
                String.valueOf(stockLimit), String.valueOf(maxItems), String.valueOf(ttl.toSeconds()),
                String.valueOf(System.currentTimeMillis()));
        return result == null ? RESULT_NOT_FOUND : result.intValue();
    }

    @Override
    public int updateQuantity(Long userId, Long skuId, int quantity, int maxQuantity,
                              int stockLimit, Duration ttl) {
        Long result = redis.execute(UPDATE, List.of(key(userId)),
                String.valueOf(skuId), String.valueOf(quantity), String.valueOf(maxQuantity),
                String.valueOf(stockLimit), String.valueOf(ttl.toSeconds()));
        return result == null ? RESULT_NOT_FOUND : result.intValue();
    }

    @Override
    public boolean remove(Long userId, Long skuId, Duration ttl) {
        Long removed = redis.opsForHash().delete(key(userId),
                QTY_PREFIX + skuId, SEL_PREFIX + skuId, AT_PREFIX + skuId);
        if (removed != null && removed > 0) {
            redis.expire(key(userId), ttl);
            return true;
        }
        return false;
    }

    @Override
    public List<StoredCartItem> items(Long userId, Duration ttl) {
        String key = key(userId);
        Map<Object, Object> raw = redis.opsForHash().entries(key);
        if (raw.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> quantities = new HashMap<>();
        Map<Long, Boolean> selections = new HashMap<>();
        Map<Long, Long> addedAt = new HashMap<>();
        raw.forEach((fieldKey, value) -> {
            String field = String.valueOf(fieldKey);
            String text = String.valueOf(value);
            try {
                if (field.startsWith(QTY_PREFIX)) {
                    quantities.put(Long.parseLong(field.substring(QTY_PREFIX.length())), Integer.parseInt(text));
                } else if (field.startsWith(SEL_PREFIX)) {
                    selections.put(Long.parseLong(field.substring(SEL_PREFIX.length())), "1".equals(text));
                } else if (field.startsWith(AT_PREFIX)) {
                    addedAt.put(Long.parseLong(field.substring(AT_PREFIX.length())), Long.parseLong(text));
                }
            } catch (NumberFormatException ignored) {
                // 单个损坏字段不应让整辆购物车不可用；按缺失处理，下次写入会覆盖
            }
        });
        List<StoredCartItem> items = new ArrayList<>(quantities.size());
        quantities.forEach((skuId, quantity) -> items.add(new StoredCartItem(
                skuId, quantity, selections.getOrDefault(skuId, true))));
        items.sort(Comparator.comparing(item -> addedAt.getOrDefault(item.skuId(), Long.MAX_VALUE)));
        if (!items.isEmpty()) {
            redis.expire(key, ttl);
        }
        return items;
    }

    @Override
    public Optional<StoredCartItem> find(Long userId, Long skuId) {
        Object qty = redis.opsForHash().get(key(userId), QTY_PREFIX + skuId);
        if (qty == null) {
            return Optional.empty();
        }
        Object selected = redis.opsForHash().get(key(userId), SEL_PREFIX + skuId);
        return Optional.of(new StoredCartItem(skuId, Integer.parseInt(String.valueOf(qty)),
                selected == null || "1".equals(String.valueOf(selected))));
    }

    @Override
    public int setSelected(Long userId, List<Long> skuIds, boolean selected, Duration ttl) {
        List<String> args = new ArrayList<>();
        args.add(selected ? "1" : "0");
        args.add(String.valueOf(ttl.toSeconds()));
        skuIds.stream().filter(java.util.Objects::nonNull).distinct()
                .forEach(skuId -> args.add(String.valueOf(skuId)));
        Long changed = redis.execute(SELECT, List.of(key(userId)), args.toArray());
        return changed == null ? 0 : changed.intValue();
    }

    private String key(Long userId) {
        return MallCacheKeys.cart(userId);
    }

    private static DefaultRedisScript<Long> script(String text) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(text);
        script.setResultType(Long.class);
        return script;
    }
}
