# Getting Started with Atlas Search in Java

This repository contains examples of using Atlas Search with Java.

The First Search Example code was written for the article ["Using Atlas Search From Java"](https://www.mongodb.com/developer/products/atlas/atlas-search-java/).

This example uses the `run` target and searches the `default` index.

In order to run the `FirstSearchExample`, follow these steps:

  * Add the [sample collections](https://www.mongodb.com/docs/atlas/sample-data/) to your Atlas cluster
    * If you're not already an Atlas user, [get started with Atlas](https://www.mongodb.com/docs/atlas/getting-started/)
  * [Create an Atlas Search index](https://www.mongodb.com/docs/atlas/atlas-search/tutorial/create-index/) on the `movies` collection, named `default`
  * Run the example program:
    `ATLAS_URI="<<insert your connection string here>>" ./gradlew run`

## Questions?

Questions about this repo or how to use Atlas Search and Java together?  Ask them in the [MongoDB Community](https://community.mongodb.com).