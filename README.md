# Getting Started with Atlas Search in Java

This repository contains examples of using Atlas Search with Java.

## First Search Example

This First Search Example code was written for the article ["Using Atlas Search From Java"](https://www.mongodb.com/developer/products/atlas/atlas-search-java/).

This example uses the `run` target and searches the `default` index.

In order to run the `FirstSearchExample`, follow these steps:

  * Add the [sample collections](https://www.mongodb.com/docs/atlas/sample-data/) to your Atlas cluster
    * If you're not already an Atlas user, [get started with Atlas](https://www.mongodb.com/docs/atlas/getting-started/)
  * [Create an Atlas Search index](https://www.mongodb.com/docs/atlas/atlas-search/tutorial/create-index/) on the `movies` collection, named `default`
  * Run the example program:
    `ATLAS_URI="<<insert your connection string here>>" ./gradlew run`

## Search Server Example

This Search Server example code was written for the article
("How to Build a Search Service in Java with MongoDB")[TBD].
It uses the `jettyRun` target and searches the `movies_index`

To run the search server locally, follow these steps:

  * Add the [sample collections](https://www.mongodb.com/docs/atlas/sample-data/) to your Atlas cluster
    * If you're not already an Atlas user, [get started with Atlas](https://www.mongodb.com/docs/atlas/getting-started/)
  * [Create an Atlas Search index](https://www.mongodb.com/docs/atlas/atlas-search/tutorial/create-index/) on the `movies` collection, named `movies_index`, using the index
    configuration below.
  * Run the search service:
    `ATLAS_URI="<<insert your connection string here>>" ./gradlew jettyRun`
  * Visit [http://localhost:8080](http://localhost:8080)

`movies_index` index configuration (JSON):
```
{
  "analyzer": "lucene.english",
  "searchAnalyzer": "lucene.english",
  "mappings": {
    "dynamic": true,
    "fields": {
      "cast": [
        {
          "type": "token"
        },
        {
          "type": "string"
        }
      ],
      "genres": [
        {
          "type": "token"
        },
        {
          "type": "string"
        }
      ]
    }
  }
}
```

## Questions?

Questions about this repo or how to use Atlas Search and Java together?  Ask them in the [MongoDB Community](https://community.mongodb.com).