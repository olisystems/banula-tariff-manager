package com.banula.tariffmanager.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.banula.openlib.web3.utils.Hashable;
import com.banula.openlib.web3.utils.Hashing;
import com.banula.tariffmanager.config.ApplicationConfiguration;
import com.banula.tariffmanager.model.hash.HashRequest;
import com.banula.tariffmanager.model.hash.HashResponse;
import com.banula.tariffmanager.model.hash.PublishedHash;
import com.banula.tariffmanager.model.hash.PublishedHashResponse;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@AllArgsConstructor
public class HashingClient {

    private final RestTemplate restTemplate;
    private final ApplicationConfiguration applicationConfiguration;

    public <T extends Hashable> PublishedHash hashAndPublish(T object, String tag) {
        try {
            // Init object mapper
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            objectMapper.findAndRegisterModules();

            // Hash object
            String hash = this.hash(object);

            // Create hashing request
            HashRequest hashRequest = new HashRequest();
            hashRequest.setPayload(hash);
            hashRequest.setTags(tag);

            String requestBody = objectMapper.writeValueAsString(hashRequest);

            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<HashResponse> response = restTemplate.exchange(
                    applicationConfiguration.getHashingServiceUrl() + "/hash",
                    HttpMethod.POST,
                    entity,
                    HashResponse.class);
            String transactionHash = response.getBody().getTransactionHash();

            PublishedHash publishedHash = new PublishedHash();
            publishedHash.setTransactionHash(transactionHash);
            publishedHash.setHash(hash);

            return publishedHash;
        } catch (Exception e) {
            log.error("Error while publishing hash, error message: " + e.getLocalizedMessage());
            e.printStackTrace();
            return null;
        }
    }

    public <T extends Hashable> String hash(T object) {
        String encoded = object.encode();
        log.info("Encoded object: " + encoded);
        return Hashing.keccak256Hash(encoded);
    }

    public String getDataByTag(String tag) {
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(null, headers);
            ResponseEntity<PublishedHashResponse> response = restTemplate.exchange(
                    applicationConfiguration.getHashingServiceUrl() + "/hash/" + tag,
                    HttpMethod.GET,
                    entity,
                    PublishedHashResponse.class);
            return response.getBody().getValue();
        } catch (Exception e) {
            log.error("Error while retrieving hash by tag, error message: " + e.getLocalizedMessage());
            e.printStackTrace();
            return null;
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        return headers;
    }
}
