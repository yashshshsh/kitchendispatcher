package com.project.kitchen_dispatch.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "riders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    private Double latitude;

    private Double longitude;

    @Column(nullable = false)
    private Boolean available = true;

    @Column(nullable = false)
    private Boolean active = true;
}