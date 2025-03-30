package com.diakonie.network_flow.service;

import com.diakonie.network_flow.model.Task;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@Service
public class ProjectDataService {

    private static final Logger log = LoggerFactory.getLogger(ProjectDataService.class);

    private final ObjectMapper objectMapper;

    /**
     * Constructor injecting Jackson's ObjectMapper.
     * @param objectMapper The Jackson ObjectMapper instance provided by Spring.
     */
    public ProjectDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Loads a list of tasks from a JSON file located in the classpath.
     *
     * @param classpathResourcePath The path to the JSON file relative to the classpath root
     *                              (for example: "data/tasks.json" corresponds to src/main/resources/data/tasks.json).
     * @return A List of Task objects, or an empty list if the file is not found or a parsing error occurs.
     */
    public List<Task> loadTasksFromJson(String classpathResourcePath) {
        // TypeReference is needed by Jackson to correctly deserialize into a generic type (List<Task>)!
        // Without it, Jackson wouldn't know the specific type of objects the List should contain
        TypeReference<List<Task>> typeReference = new TypeReference<List<Task>>() {};
        InputStream inputStream = null;

        try {
            log.info("Attempting to load tasks from classpath resource: {}", classpathResourcePath);
            // ClassPathResource is a Spring utility to reliably load resources from the classpath,
            // regardless of whether the application is run from an IDE or a packaged JAR file
            ClassPathResource resource = new ClassPathResource(classpathResourcePath);

            if (!resource.exists()) {
                log.error("Resource not found at path: {}", classpathResourcePath);

                return Collections.emptyList();
            }

            inputStream = resource.getInputStream();
            // objectMapper.readValue performs the JSON parsing and data binding to the List<Task>
            List<Task> tasks = objectMapper.readValue(inputStream, typeReference);
            log.info("Successfully loaded {} tasks from {}", tasks.size(), classpathResourcePath);

            return tasks;

        } catch (IOException e) {
            log.error("Failed to load or parse tasks from JSON file: {}", classpathResourcePath, e);

            return Collections.emptyList(); // Return empty list on error
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("Failed to close input stream for resource: {}", classpathResourcePath, e);
                }
            }
        }
    }
}