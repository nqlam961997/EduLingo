package com.edulingo.dto;

import java.util.List;

public class TopicDto {

    public record Topic(String id, String name, String description, String icon,
                         String characterName, String characterRole, String characterAvatar) {}

    public static final List<Topic> TOPICS = List.of(
            new Topic("restaurant", "At the Restaurant", "Order food, ask about the menu, make a reservation", "🍽️",
                    "Maria", "Friendly Waitress", "👩‍🍳"),
            new Topic("airport", "At the Airport", "Check in, ask for directions, handle delays", "✈️",
                    "James", "Airport Staff", "👨‍✈️"),
            new Topic("shopping", "Shopping", "Buy clothes, ask for sizes, bargain", "🛍️",
                    "Sophie", "Shop Assistant", "🧑‍💼"),
            new Topic("hotel", "At the Hotel", "Check in/out, request services, complain", "🏨",
                    "David", "Hotel Receptionist", "🧑‍💻"),
            new Topic("doctor", "At the Doctor", "Describe symptoms, understand prescriptions", "🏥",
                    "Dr. Sarah", "Family Doctor", "👩‍⚕️"),
            new Topic("job_interview", "Job Interview", "Answer questions, talk about experience", "💼",
                    "Mr. Thompson", "HR Manager", "👨‍💼"),
            new Topic("travel", "Travel & Directions", "Ask for directions, take a taxi, buy tickets", "🗺️",
                    "Carlos", "Local Guide", "🧑‍🦱"),
            new Topic("school", "At School", "Talk to teachers, discuss homework, campus life", "🎓",
                    "Ms. Emily", "English Teacher", "👩‍🏫"),
            new Topic("daily_routine", "Daily Routine", "Describe your day, habits, schedules", "🌅",
                    "Alex", "Friendly Neighbor", "🧑"),
            new Topic("sports", "Sports & Hobbies", "Discuss favorite sports, invite to play", "⚽",
                    "Mike", "Sports Coach", "🏋️"),
            new Topic("technology", "Technology", "Discuss gadgets, apps, social media", "💻",
                    "Luna", "Tech Enthusiast", "👩‍💻"),
            new Topic("environment", "Environment", "Talk about nature, pollution, recycling", "🌿",
                    "Oliver", "Eco Volunteer", "🧑‍🌾")
    );
}
