package com.mongodb.atlas;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.search.SearchOperator;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Aggregates.search;

import static com.mongodb.client.model.Projections.excludeId;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Projections.meta;
import static com.mongodb.client.model.Projections.metaSearchScore;
import static com.mongodb.client.model.search.SearchOperator.compound;
import static com.mongodb.client.model.search.SearchOperator.text;
import static com.mongodb.client.model.search.SearchOptions.searchOptions;
import static com.mongodb.client.model.search.SearchPath.fieldPath;

public class FirstSearchExample {
    public static void main(String[] args) throws Exception {
        // Set ATLAS_URI in your environment
        String uri = System.getenv("ATLAS_URI");
        if (uri == null) {
            throw new Exception("ATLAS_URI must be specified");
        }

        MongoClient mongoClient = MongoClients.create(uri);
        MongoDatabase database = mongoClient.getDatabase("sample_mflix");
        MongoCollection<Document> collection = database.getCollection("movies");

        SearchOperator genresClause = compound()
                .must(Arrays.asList(
                        text(fieldPath("genres"),"Drama"),
                        text(fieldPath("genres"), "Romance")
                ));

        Document searchQuery = new Document("phrase",
                                    new Document("query", "keanu reeves")
                                         .append("path", "cast"));

        Bson searchStage = search(
                compound()
                        .filter(List.of(genresClause))
                        .must(List.of(SearchOperator.of(searchQuery))),
                searchOptions().option("scoreDetails", true)
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
            System.out.println();
        });

        // Print the explain output, which shows query interpretation details
        System.out.println("Explain:");
        System.out.println(format(aggregationResults.explain().toBsonDocument()));

        mongoClient.close();
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
