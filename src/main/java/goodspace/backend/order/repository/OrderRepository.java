package goodspace.backend.order.repository;

import goodspace.backend.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByApproveResult_OrderId(String orderId);
    Optional<Order> findByOrderOutId(String orderOutId);

}
