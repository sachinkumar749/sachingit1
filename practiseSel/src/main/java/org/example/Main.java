package org.example;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Main {
    public static void main(String[] args) {
        SprintInput input = sampleInput();
        SprintPrediction prediction = SprintPredictor.predict(input);

        System.out.println(
                "Sprint success probability: " + prediction.successProbabilityPercent + "%. " + prediction.riskSummary
        );

        if (prediction.likelyToMiss.isEmpty()) {
            System.out.println("No tickets are predicted to miss the sprint.");
            return;
        }

        System.out.println("Tickets likely to miss sprint:");
        for (Ticket ticket : prediction.likelyToMiss) {
            String blockerTag = ticket.hasBlocker ? " blocker" : "";
            System.out.println(
                    "- " + ticket.id + ": " + ticket.title + " (assigned to " + ticket.assignedDeveloper
                            + ", complexity " + ticket.complexity + blockerTag + ")"
            );
        }
    }

    private static SprintInput sampleInput() {
        List<Developer> developers = new ArrayList<>();
        developers.add(new Developer("Ava", 1.0, 0.92, 4));
        developers.add(new Developer("Raj", 1.1, 0.85, 4));
        developers.add(new Developer("Mia", 0.9, 0.90, 4));
        developers.add(new Developer("Luis", 1.0, 0.90, 4));

        List<Ticket> tickets = new ArrayList<>();
        tickets.add(new Ticket("TCK-101", "Payment API failure", 8, true, "Raj"));
        tickets.add(new Ticket("TCK-102", "SSO integration", 8, true, "Raj"));
        tickets.add(new Ticket("TCK-103", "Billing email fixes", 5, false, "Raj"));
        tickets.add(new Ticket("TCK-104", "Signup copy updates", 5, false, "Ava"));
        tickets.add(new Ticket("TCK-105", "Telemetry dashboard", 4, false, "Ava"));
        tickets.add(new Ticket("TCK-106", "Cache tuning", 4, false, "Mia"));
        tickets.add(new Ticket("TCK-107", "Data cleanup", 3, false, "Mia"));
        tickets.add(new Ticket("TCK-108", "Webhook retries", 5, false, "Luis"));
        tickets.add(new Ticket("TCK-109", "UI polish", 2, false, "Luis"));

        int pastVelocity = 42;
        int currentWorkload = SprintPredictor.sumComplexity(tickets);

        return new SprintInput(pastVelocity, currentWorkload, tickets, developers);
    }

    static class SprintInput {
        final int pastVelocity;
        final int currentWorkload;
        final List<Ticket> tickets;
        final List<Developer> developers;

        SprintInput(int pastVelocity, int currentWorkload, List<Ticket> tickets, List<Developer> developers) {
            this.pastVelocity = pastVelocity;
            this.currentWorkload = currentWorkload;
            this.tickets = tickets;
            this.developers = developers;
        }
    }

    static class Developer {
        final String name;
        final double focusFactor;
        final double reliability;
        final int wipLimit;

        Developer(String name, double focusFactor, double reliability, int wipLimit) {
            this.name = name;
            this.focusFactor = focusFactor;
            this.reliability = reliability;
            this.wipLimit = wipLimit;
        }
    }

    static class Ticket {
        final String id;
        final String title;
        final int complexity;
        final boolean hasBlocker;
        final String assignedDeveloper;

        Ticket(String id, String title, int complexity, boolean hasBlocker, String assignedDeveloper) {
            this.id = id;
            this.title = title;
            this.complexity = complexity;
            this.hasBlocker = hasBlocker;
            this.assignedDeveloper = assignedDeveloper;
        }
    }

    static class SprintPrediction {
        final int successProbabilityPercent;
        final String riskSummary;
        final List<Ticket> likelyToMiss;

        SprintPrediction(int successProbabilityPercent, String riskSummary, List<Ticket> likelyToMiss) {
            this.successProbabilityPercent = successProbabilityPercent;
            this.riskSummary = riskSummary;
            this.likelyToMiss = likelyToMiss;
        }
    }

    static class SprintPredictor {
        private static final int HIGH_COMPLEXITY_THRESHOLD = 8;
        private static final double BLOCKER_PENALTY = 0.06;
        private static final double OVERLOADED_PENALTY = 0.03;
        private static final double HIGH_COMPLEXITY_PENALTY = 0.01;
        private static final double OVERLOAD_BUFFER = 1.05;

        static SprintPrediction predict(SprintInput input) {
            if (input.tickets.isEmpty() || input.developers.isEmpty()) {
                return new SprintPrediction(0, "Insufficient data to predict sprint success.", new ArrayList<>());
            }

            int totalWorkload = input.currentWorkload > 0 ? input.currentWorkload : sumComplexity(input.tickets);
            Map<String, Double> capacityByDeveloper = capacityByDeveloper(input);
            Set<String> overloadedDevelopers = findOverloadedDevelopers(input, capacityByDeveloper);

            int blockerCount = 0;
            int blockersOnOverloaded = 0;
            int highComplexityCount = 0;
            for (Ticket ticket : input.tickets) {
                if (ticket.hasBlocker) {
                    blockerCount++;
                    if (overloadedDevelopers.contains(ticket.assignedDeveloper)) {
                        blockersOnOverloaded++;
                    }
                }
                if (ticket.complexity >= HIGH_COMPLEXITY_THRESHOLD) {
                    highComplexityCount++;
                }
            }

            List<Ticket> likelyToMiss = findLikelyToMiss(input, capacityByDeveloper, overloadedDevelopers);

            double totalCapacity = 0.0;
            for (double capacity : capacityByDeveloper.values()) {
                totalCapacity += capacity;
            }

            double baseProbability = totalWorkload == 0 ? 0.0 : totalCapacity / totalWorkload;
            double penalty = blockerCount * BLOCKER_PENALTY
                    + overloadedDevelopers.size() * OVERLOADED_PENALTY
                    + highComplexityCount * HIGH_COMPLEXITY_PENALTY;
            double probability = clamp(baseProbability - penalty, 0.0, 1.0) * 100.0;
            int successProbabilityPercent = (int) Math.round(probability);

            String riskSummary = buildRiskSummary(blockersOnOverloaded, blockerCount, overloadedDevelopers.size());
            return new SprintPrediction(successProbabilityPercent, riskSummary, likelyToMiss);
        }

        static int sumComplexity(List<Ticket> tickets) {
            int total = 0;
            for (Ticket ticket : tickets) {
                total += ticket.complexity;
            }
            return total;
        }

        private static Map<String, Double> capacityByDeveloper(SprintInput input) {
            Map<String, Double> capacityByDeveloper = new HashMap<>();
            double totalFocus = 0.0;
            for (Developer developer : input.developers) {
                totalFocus += developer.focusFactor;
            }

            if (totalFocus == 0.0) {
                return capacityByDeveloper;
            }

            for (Developer developer : input.developers) {
                double share = developer.focusFactor / totalFocus;
                double capacity = input.pastVelocity * share * developer.reliability;
                capacityByDeveloper.put(developer.name, capacity);
            }

            return capacityByDeveloper;
        }

        private static Set<String> findOverloadedDevelopers(
                SprintInput input,
                Map<String, Double> capacityByDeveloper
        ) {
            Map<String, Integer> pointsByDeveloper = new HashMap<>();
            Map<String, Integer> countByDeveloper = new HashMap<>();

            for (Ticket ticket : input.tickets) {
                pointsByDeveloper.put(
                        ticket.assignedDeveloper,
                        pointsByDeveloper.getOrDefault(ticket.assignedDeveloper, 0) + ticket.complexity
                );
                countByDeveloper.put(
                        ticket.assignedDeveloper,
                        countByDeveloper.getOrDefault(ticket.assignedDeveloper, 0) + 1
                );
            }

            Set<String> overloadedDevelopers = new HashSet<>();
            for (Developer developer : input.developers) {
                int assignedPoints = pointsByDeveloper.getOrDefault(developer.name, 0);
                int assignedCount = countByDeveloper.getOrDefault(developer.name, 0);
                double capacity = capacityByDeveloper.getOrDefault(developer.name, 0.0);

                if (capacity > 0.0 && assignedPoints > capacity * OVERLOAD_BUFFER) {
                    overloadedDevelopers.add(developer.name);
                }
                if (assignedCount > developer.wipLimit) {
                    overloadedDevelopers.add(developer.name);
                }
            }

            return overloadedDevelopers;
        }

        private static List<Ticket> findLikelyToMiss(
                SprintInput input,
                Map<String, Double> capacityByDeveloper,
                Set<String> overloadedDevelopers
        ) {
            Map<String, List<Ticket>> ticketsByDeveloper = new HashMap<>();
            for (Ticket ticket : input.tickets) {
                ticketsByDeveloper
                        .computeIfAbsent(ticket.assignedDeveloper, key -> new ArrayList<>())
                        .add(ticket);
            }

            List<Ticket> likelyToMiss = new ArrayList<>();
            for (Developer developer : input.developers) {
                List<Ticket> assignedTickets = ticketsByDeveloper.get(developer.name);
                if (assignedTickets == null || assignedTickets.isEmpty()) {
                    continue;
                }

                boolean overloaded = overloadedDevelopers.contains(developer.name);
                assignedTickets.sort(
                        Comparator.comparingDouble(ticket -> riskScore(ticket, developer, overloaded))
                );

                double capacity = capacityByDeveloper.getOrDefault(developer.name, 0.0);
                double used = 0.0;
                for (Ticket ticket : assignedTickets) {
                    if (used + ticket.complexity <= capacity) {
                        used += ticket.complexity;
                    } else {
                        likelyToMiss.add(ticket);
                    }
                }
            }

            return likelyToMiss;
        }

        private static double riskScore(Ticket ticket, Developer developer, boolean overloaded) {
            double score = ticket.complexity / (double) HIGH_COMPLEXITY_THRESHOLD;
            if (ticket.hasBlocker) {
                score += 1.0;
            }
            if (overloaded) {
                score += 0.5;
            }
            if (developer.reliability < 0.9) {
                score += 0.2;
            }
            return score;
        }

        private static String buildRiskSummary(
                int blockersOnOverloaded,
                int blockerCount,
                int overloadedDeveloperCount
        ) {
            if (blockersOnOverloaded > 0) {
                return "High risk due to " + blockersOnOverloaded + " blocker"
                        + (blockersOnOverloaded == 1 ? "" : "s")
                        + " assigned to overloaded dev"
                        + (overloadedDeveloperCount == 1 ? "" : "s") + ".";
            }

            if (blockerCount > 0 && overloadedDeveloperCount > 0) {
                return "Risk due to " + blockerCount + " blocker"
                        + (blockerCount == 1 ? "" : "s")
                        + " and " + overloadedDeveloperCount + " overloaded dev"
                        + (overloadedDeveloperCount == 1 ? "" : "s") + ".";
            }

            if (blockerCount > 0) {
                return "Risk due to " + blockerCount + " blocker" + (blockerCount == 1 ? "" : "s") + ".";
            }

            if (overloadedDeveloperCount > 0) {
                return "Risk due to " + overloadedDeveloperCount + " overloaded dev"
                        + (overloadedDeveloperCount == 1 ? "" : "s") + ".";
            }

            return "Risk is low based on current workload.";
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
