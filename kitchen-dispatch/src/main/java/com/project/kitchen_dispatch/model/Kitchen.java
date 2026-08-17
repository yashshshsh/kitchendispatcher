package com.project.kitchendispatch.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kitchens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Kitchen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false)
    private Boolean active = true;
}