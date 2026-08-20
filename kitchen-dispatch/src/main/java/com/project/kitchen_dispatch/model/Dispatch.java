package com.project.kitchen_dispatch.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "dispatches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(
            name = "order_id",
            nullable = false,
            unique = true
    )
    private Order order;

    @ManyToOne
    @JoinColumn(
            name = "rider_id",
            nullable = false
    )
    private Rider rider;

    @Column(nullable = false)
    private String status = "ASSIGNED";

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    private LocalDateTime pickedUpAt;

    private LocalDateTime deliveredAt;

    /*
     * Estimated delivery time calculated
     * at the time of dispatch.
     */
    private LocalDateTime estimatedDeliveryTime;

    /*
     * Difference between actual delivery time
     * and estimated delivery time.
     *
     * Positive  -> delivered late
     * Negative  -> delivered early
     */
    private Long etaErrorMinutes;

    /*
     * ============================================================
     * HISTORICAL ETA FEATURES
     * ============================================================
     *
     * These values are captured at dispatch time.
     *
     * They must NOT be recalculated later using the rider's
     * current location because the rider may have moved.
     *
     * These fields will eventually become features for the
     * ML-based ETA prediction model.
     */

    /*
     * Distance from rider's location to the kitchen
     * at the moment of dispatch.
     */
    private Double riderToKitchenDistanceKm;

    /*
     * Distance from kitchen to customer's delivery location
     * at the moment of dispatch.
     */
    private Double kitchenToCustomerDistanceKm;

    /*
     * Total distance involved in the delivery.
     *
     * rider -> kitchen
     * +
     * kitchen -> customer
     */
    private Double totalDistanceKm;
}