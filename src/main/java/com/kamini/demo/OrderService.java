package com.kamini.demo;

import java.util.List;
import java.util.UUID;

/**
 * Demo service — used to showcase the Latency Budget Analyzer plugin.
 * Right-click inside any method below → ⚡ Analyze Latency Budget
 */
public class OrderService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final KafkaTemplate kafkaTemplate = new KafkaTemplate();
    private final RedisTemplate redisTemplate = new RedisTemplate();
    private final OrderRepository orderRepository = new OrderRepository();

    /**
     * Places an order — great method to analyze for latency hotspots.
     * Contains: 3× HTTP calls, 1× DB save, 1× Kafka publish, 1× Redis write.
     */
    public Order placeOrder(String userId, String productId, int quantity) {
        // 1. Validate user via external identity service (HTTP)
        String userDetails = restTemplate.getForObject(
                "https://identity-service/users/" + userId);

        // 2. Fetch product pricing from pricing service (HTTP)
        String pricing = restTemplate.getForObject(
                "https://pricing-service/products/" + productId);

        // 3. Persist order to database (DB)
        Order order = new Order(userId, productId, quantity);
        orderRepository.save(order);

        // 4. Cache order in Redis for fast lookup (Redis)
        redisTemplate.opsForValue().set("order:" + order.getId(), order);

        // 5. Publish order event to Kafka (Kafka)
        kafkaTemplate.send("order-events", order.getId(), "ORDER_PLACED");

        // 6. Notify shipping service (HTTP)
        restTemplate.postForObject("https://shipping-service/notify", order);

        return order;
    }

    /**
     * Fetches order history — shows DB + Redis caching pattern.
     */
    public List<Order> getOrderHistory(String userId) {
        // Check Redis cache first
        Object cached = redisTemplate.opsForValue().get("history:" + userId);
        if (cached != null) {
            return (List<Order>) cached;
        }

        // Cache miss — load from database
        List<Order> history = orderRepository.findAll();

        // Write back to Redis
        redisTemplate.opsForValue().set("history:" + userId, history);

        return history;
    }

    // ── Stub classes — real names trigger the analyzer's pattern matching ──

    static class RestTemplate {
        String getForObject(String url) { return ""; }
        String postForObject(String url, Object body) { return ""; }
    }

    static class KafkaTemplate {
        void send(String topic, String key, String value) {}
    }

    static class RedisTemplate {
        ValueOps opsForValue() { return new ValueOps(); }
        static class ValueOps {
            void set(String key, Object value) {}
            Object get(String key) { return null; }
        }
    }

    static class OrderRepository {
        Order save(Order o) { return o; }
        List<Order> findAll() { return List.of(); }
    }

    static class Order {
        private final String id = UUID.randomUUID().toString();
        Order(String userId, String productId, int qty) {}
        public String getId() { return id; }
    }
}
