package com.project.kitchen_dispatch.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String customerPhone;

    @Column(nullable = false)
    private String deliveryAddress;

    private Double deliveryLatitude;

    private Double deliveryLongitude;

    @Column(nullable = false)
    private String status = "PLACED";

    @ManyToOne
    @JoinColumn(name = "kitchen_id", nullable = false)
    private Kitchen kitchen;

    private Integer estimatedPreparationTime;

    private Integer actualPreparationTime;

    /*
     * Exact time at which the order was created.
     *
     * Used by the dispatch engine to calculate:
     *
     * Order Created Time
     *        +
     * Preparation Time
     *        =
     * Food Ready Time
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /*
     * Automatically executed by JPA before
     * inserting a new Order.
     */
    @PrePersist
    protected void onCreate() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = "PLACED";
        }
    }
}