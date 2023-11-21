package com.mongodb.atlas;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.search.SearchCount;
import com.mongodb.client.model.search.SearchOperator;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.BsonArray;
import org.bson.Document;
import org.bson.conversions.Bson;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Aggregates.search;
import static com.mongodb.client.model.Aggregates.skip;
import static com.mongodb.client.model.Projections.excludeId;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Projections.meta;
import static com.mongodb.client.model.Projections.metaSearchScore;
import static com.mongodb.client.model.search.SearchOperator.compound;
import static com.mongodb.client.model.search.SearchOptions.searchOptions;

public class SearchServlet extends HttpServlet {
  private MongoCollection<Document> collection;
  private String index_name;

  @Override
  public void init(ServletConfig config) throws ServletException {
    String uri = System.getenv("ATLAS_URI");
    if (uri == null) {
      throw new ServletException("ATLAS_URI must be specified");
    }

    // TODO: get these from servlet config
    String database_name = config.getInitParameter("database");
    String collection_name = config.getInitParameter("collection");
    index_name = config.getInitParameter("index");

    MongoClient mongoClient = MongoClients.create(uri);
    MongoDatabase database = mongoClient.getDatabase(database_name);
    collection = database.getCollection(collection_name);

    super.init(config);
  }

  /**
   *
   * @param request an {@link HttpServletRequest} object that contains the request the client has made of the servlet
   * @param response an {@link HttpServletResponse} object that contains the response the servlet sends to the client
   *
   *  /path?q=<query>[&skip=N&fl=<field list>]
   *                  &filter=genre:Adventure&filter=...
   *
   * @throws ServletException
   * @throws IOException
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Map<String, String[]> params = request.getParameterMap();
    String q = params.get("q")[0];
    String skip_param = request.getParameter("skip");
    String limit_param = request.getParameter("limit");
    String fields_param = request.getParameter("fields");

    int skip = skip_param == null ? 0 : Integer.valueOf(skip_param);
    int limit = limit_param == null ? 10 : Integer.valueOf(limit_param);

    skip = Math.min(100,skip);
    limit = Math.min(25,limit);

    String[] fields = fields_param.split(",");

    // TODO: parameterize path
    Document searchQuery = new Document("text",
        new Document("query", q)
            .append("path", "plot"));
    Bson searchStage = search(
        SearchOperator.of(searchQuery),
        searchOptions()
            .option("scoreDetails", true)
            .index(index_name)
            .count(SearchCount.total())
    );

    AggregateIterable<Document> aggregationResults = collection.aggregate(List.of(
        searchStage,
        project(fields(
            include(List.of(fields)),
            metaSearchScore("_score"),
            meta("_scoreDetails", "searchScoreDetails"))),
        skip(skip),
        limit(limit)));

    Document response_doc = new Document();
    response_doc.put("meta",new Document()
                       .append("q",q)
                       .append("skip",skip)
                       .append("limit",limit));
    BsonArray docs = new BsonArray();
    for (Document aggregationResult : aggregationResults) {
      docs.add(aggregationResult.toBsonDocument());
    }
    response_doc.put("docs",docs);

    response.setContentType("text/json");
    PrintWriter writer = response.getWriter();
    writer.println(response_doc.toJson());
    writer.close();
  }
}
