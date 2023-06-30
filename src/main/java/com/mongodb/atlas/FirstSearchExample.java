package com.mongodb.atlas;

import com.mongodb.client.*;
import com.mongodb.client.model.search.*;
import org.bson.*;
import org.bson.conversions.*;
import org.bson.json.*;
import java.util.*;

import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Aggregates.search;

import static com.mongodb.client.model.Projections.*;
import static com.mongodb.client.model.search.SearchPath.*;

public class FirstSearchExample {
    public static void main(String[] args) {
        // Set ATLAS_URI in your environment
        String uri = System.getenv("ATLAS_URI");

        try (MongoClient mongoClient = MongoClients.create(uri)) {
            MongoDatabase database = mongoClient.getDatabase("sample_mflix");
            MongoCollection<Document> collection = database.getCollection("movies");

            SearchOperator genresClause = SearchOperator.compound()
                    .must(Arrays.asList(
                            SearchOperator.text(fieldPath("genres"),"Drama"),
                            SearchOperator.text(fieldPath("genres"), "Romance")
                    ));

            Document searchQuery = new Document("phrase",
                                        new Document("query", "keanu reeves")
                                             .append("path", fieldPath("cast")));

            Bson searchStage = search(
                    SearchOperator.compound()
                            .filter(List.of(genresClause))
                            .must(List.of(SearchOperator.of(searchQuery))),
                    SearchOptions.searchOptions().option("scoreDetails", BsonBoolean.TRUE)
            );

            // Create a pipeline that searches, projects, and limits the number of results returned.
            AggregateIterable<Document> aggregationResults = collection.aggregate(Arrays.asList(
                    searchStage,
                    project(fields(excludeId(),
                            include("title", "cast", "genres"),
                            metaSearchScore("score"),
                            meta("scoreDetails", "searchScoreDetails"))),
                    limit(10)));

            // Print out each returned result
            //aggregation_spec.forEach(doc -> System.out.println(formatJSON(doc)));
            aggregationResults.forEach(doc -> {
                System.out.println(doc.get("title"));
                System.out.println("  Cast: " + doc.get("cast"));
                System.out.println("  Genres: " + doc.get("genres"));
                System.out.println("  Score:" + doc.get("score"));
                // printScoreDetails(3, doc.toBsonDocument().getDocument("scoreDetails"));
                System.out.println("");
            });

            // Print the explain output, which shows query interpretation details
            System.out.println("Explain:");
            System.out.println(format(aggregationResults.explain().toBsonDocument()));
        } catch (Exception e) {
            System.err.println(e.getLocalizedMessage());
        }

    }

    private static void printScoreDetails(int indentLevel, BsonDocument scoreDetails) {
        String spaces = " ".repeat(indentLevel);
        System.out.println(spaces + scoreDetails.getDouble("value").doubleValue() + ", " +
                           scoreDetails.getString("description").getValue());
        BsonArray details = scoreDetails.getArray("details");
        for (org.bson.BsonValue detail : details) {
            printScoreDetails(indentLevel + 2, (BsonDocument) detail);
        }
    }

    private static String format(BsonDocument document) {
        var settings = JsonWriterSettings.builder()
                .indent(true).outputMode(JsonMode.SHELL).build();
        return document.toJson(settings);
    }
}
