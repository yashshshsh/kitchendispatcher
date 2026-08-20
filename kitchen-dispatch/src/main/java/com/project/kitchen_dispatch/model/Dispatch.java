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
     * ETA calculated for this dispatch.
     *
     * This stores the prediction that was shown
     * to the customer.
     */
    private LocalDateTime estimatedDeliveryTime;

    /*
     * Difference between actual delivery time
     * and predicted delivery time.
     *
     * Example:
     *
     * ETA      = 11:38
     * Delivered = 11:42
     *
     * etaErrorMinutes = +4
     *
     * Positive  -> delivered late
     * Negative  -> delivered early
     * Zero      -> exact prediction
     */
    private Long etaErrorMinutes;
}