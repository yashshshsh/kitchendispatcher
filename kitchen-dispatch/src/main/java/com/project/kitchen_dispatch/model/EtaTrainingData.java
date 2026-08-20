package com.project.kitchen_dispatch.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaTrainingData {

    /*
     * Estimated preparation time predicted
     * when the order was created.
     */
    private Integer estimatedPreparationTime;

    /*
     * Distance from rider to kitchen
     * at dispatch time.
     */
    private Double riderToKitchenDistanceKm;

    /*
     * Distance from kitchen to customer
     * at dispatch time.
     */
    private Double kitchenToCustomerDistanceKm;

    /*
     * Rider -> Kitchen -> Customer
     */
    private Double totalDistanceKm;

    /*
     * Hour at which the order was created.
     *
     * Example:
     * 13 = 1 PM
     */
    private Integer hourOfDay;

    /*
     * Java DayOfWeek value:
     *
     * MONDAY    = 1
     * TUESDAY   = 2
     * ...
     * SUNDAY    = 7
     */
    private Integer dayOfWeek;

    /*
     * Actual total delivery duration:
     *
     * deliveredAt - order.createdAt
     *
     * This is the ML target.
     */
    private Long actualDeliveryMinutes;
}