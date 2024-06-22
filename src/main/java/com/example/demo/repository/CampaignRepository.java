package com.example.demo.repository;

import com.example.demo.model.Campaign;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface CampaignRepository extends MongoRepository<Campaign, String> {
    @Query("{'callDetails.sessionId': ?0}")
    Campaign findByCallDetailsSessionId(String sessionId);

    Campaign findByRandomIndex(String randomIndex);
}
