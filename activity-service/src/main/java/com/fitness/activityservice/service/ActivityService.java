package com.fitness.activityservice.service;

import com.fitness.activityservice.dto.ActivityRequest;
import com.fitness.activityservice.dto.ActivityResponse;
import com.fitness.activityservice.model.Activity;
import com.fitness.activityservice.repository.ActivityRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActivityService
{

    private final ActivityRepository activityRepository;
    private final UserValidationService userValidationService;
    private final RabbitTemplate rabbitTemplate;
    @Value("${rabbitmq.exchange.name}")
    private String exchange;

    @Value("${rabbitmq.routing.key}")
    private String routingKey;

    public ActivityResponse trackActivity(ActivityRequest request)
    {
        boolean isValidUser=userValidationService.validateUser(request.getUserId());
        if(!isValidUser) throw new RuntimeException("Invalid User: "+request.getUserId());
        Activity activity=Activity.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .duration(request.getDuration())
                .caloriesBurned(request.getCaloriesBurned())
                .startTime(request.getStartTime())
                .additionalMetrics(request.getAdditionalMetrics())
                .build();

        Activity savedActivity=activityRepository.save(activity);
        // publish to rabbitmq queue for ai procesing
        try {
            rabbitTemplate.convertAndSend(exchange,routingKey,savedActivity);
        }
        catch(Exception e){
            log.error("Failed to publish activity to rabbitmq",e);
        }
        return mapToActivityResponse(savedActivity);


    }

    public List<ActivityResponse> getUserActivities(String userId) {
        List<Activity> activities = activityRepository.findByUserId(userId);
        List<ActivityResponse> responses=new ArrayList<>();
        return activities.stream()
                .map(this::mapToActivityResponse)
                .collect(Collectors.toList());

    }

    public ActivityResponse getUserActivityById(String activityId) {
        return activityRepository.findById(activityId)
                .map(this::mapToActivityResponse)
                .orElseThrow(()-> new RuntimeException("Activity not found with is : "+activityId));

    }
    private ActivityResponse mapToActivityResponse(Activity activity) {
        ActivityResponse response=new ActivityResponse();
        response.setId(activity.getId());
        response.setUserId(activity.getUserId());
        response.setType(activity.getType());
        response.setDuration(activity.getDuration());
        response.setCaloriesBurned(activity.getCaloriesBurned());
        response.setStartTime(activity.getStartTime());
        response.setAdditionalMetrics(activity.getAdditionalMetrics());
        response.setCreatedAt(activity.getCreatedAt());
        response.setUpdatedAt(activity.getUpdatedAt());
        return response;
    }

}
