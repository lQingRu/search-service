# Search Service

# Introduction

- Search service has 2 main responsibilities:
    - Index changes from MongoDB to Elasticsearch (ES)
        - It contains an embedded debezium that listens to any changes to MongoDB, pre-process and
          then index into Elasticsearch
    - Perform search
        - Constructing of ES queries to retrieve results
        - Expose RESTful APIs to consumers for search

## Scope of Project

- This project here thus include the following exploration (in the order of priority):
    - Different possible ways to design ES index(es)
    - Data manipulation and data enrichment from MongoDB to ES
    - Different query constructions
    - Different designs for Search APIs
- This project takes into consideration, 2 types of search use cases:
    - Global search
    - Structured hierarchical search

## Context

- In this project, we consider the following scenario:
    - Person contains a device and a device can contain 1:M phone numbers (i.e. storing the past
      history of all the different phone numbers)
    - See `mongodomain` package for the specific fields in each of the entities

### Use Case: Global search

To do incremental evaluation, we consider the scenarios in order with the minimal requirements:

- Filters with person's & searchable's fields
- Sort with person & searchable's fields
- Group by person
- Dynamic filters for search results

1. Parent and Searchable field scope

- M1: Denormalized
- M2: Parent-child

2. Parent and Searchable field scope with first-level context [WIP]

- E.g.: Searchable field as "Phone Number", with context from the "Phone Number" model
- M1: Denormalized
- M2: Parent-child (child being denormalized)
- M3: Grandparent-grandchild (grandparent: `person`, parent: `phone number`,
  child: `searchable field` )
- M4: Parent-child + 2nd query to get context
- M5: Denormalized (with all searchable fields in the same record) + Parent-child
    - To use `inner_hits`

3. Parent and Searchable field scope with second-level context [WIP]

- E.g.: Searchable field as "Phone Number", with context from "Device" and "Phone Number" models

# High-level comparison of approaches for entity relationships

## Approach 1 - Application-side joins: Many-to-Many relationships

- Normalized indexes
- Very similar to `mongodomain`
    - `Person`
    - `Device`
    - `Phone Number`

### Pros

- Changes are centralized
- Well-bounded index
- Good index performance
- Higher similarity with mongoDB data model = Less complex transformation

### Cons

- Run extra queries to do application-side joins at search time
    - This could still possibly be cached for `Person` entity (which highly unlikely would change
      much)
- Search & Filters only possible in 1-level (i.e. scope of the index)
    - Not possible to "Search by person name & filter by device type"

### Conclusion

- Fully normalized is too restrictive and only for simple use cases

## Approach 2 - Denormalization: Many-to-Many relationships

- `Person`, `Device`, `Phone Number`
    - Person: Device : Phone Number

### Pros

- Best search performance
- Less complex queries for searching across enities that are linked (since it is within the same
  document)

### Cons

- Very big (potentially unbounded) index
- Changes to the entity, need to re-index all documents

### Conclusion

- Not very maintainable
- Could still adopt a partial denormalization that is dependent on the growth & frequency of changes
  to entities

## Approach 3 - Nested objects model: One-to-Many relationships

- This is used for multi-valued objects as well

### Pros

- Fields within the object maintain their relationship
- Because of the way nested objects are indexed, joining the nested documents to the root document
  at query time is fast (as though it is a single document)
    - `nested` query and filter provide fast query-time joins
- Performant even with deeply nested objects

### Cons

- These nested documents are hidden, i.e. we cannot access/query them directly
    - Results returned is the whole document
- To make changes to a nested object, need to reindex the whole document
- Limited to a maximum of 10,000 nested objects per document

### Conclusion

- Useful if there is one main entity with a limited number of closely related objects (nested
  objects)

## Approach 4 - Parent-Child model: One-to-Many relationships

- Similar in nature to the `nested objects model`, but parent and child are completely different
  documents
- Parent ID needs to be specified when indexing child document
    - Parent ID serves as a routing key
- Use of `join` field datatype

### Pros

- Indexing parents is no different from any other documents
    - Parents do not need to know about their children
- Fast indexing time
- Child documents can be changed without affecting the parent or other children
- Child documents can be returned as a result of a search request

### Cons

- Parent and all of its children must reside in the same shard and same index
- Memory needed to keep the mapping of parent and children
- The more joins we have, the worse performance will be
    - Each generation of parents needs to have their string `_id` fields stored in memory, which
      consume a lot of RAM
        - Good to keep `_id` small
- Can be complex when querying across multiple levels of hierarchy or when there is a deeply nested
  parent-child relationships
- Only one `join` field mapping is allowed per index
- Cannot specify mappings for `parent` and `child` separately, can only specify the whole index

### Conclusion

- Useful if there are a lot more children than more parents
- Useful if index performance is important
- A lot of uncertainties:
    - Is parent and children document treated the same when scoring if we use a default query to
      search across parent & children documents?
    - Are we able to set the number of documents to be returned for children documents?
    - Seems like unable to search against parent & children filters
        - Potentially a dealbreaker

## Plan

1. Data models
    1. Types of fields
        1. Object / Primitive
            1. Attachment (Long text with metadata)
            2. `<look through all types of fields>`
        2. Embedded object
        3. List of objects / primitive
        4. List of embedded objects
    2. Hierarchy
        1. 3 levels
2. Search results
    1. Hierarchy
        1. 3 levels (e.g.: Person Relationship & Communications)
    2. Which should always be the "container"
    3. Access Control
3. Search behaviours
    1. Hierarchy interaction
        1. E.g.: Search a 2nd-level document but filter by 1st and 2nd level fields
    2. Field behaviour (character filters, tokenizer, token filters)
        1. May need to go through the whole list of fields
        2. Access control

## Resources

- [Join queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/joining-queries.html)
- [Handling relationships](https://www.elastic.co/guide/en/elasticsearch/guide/current/relations.html)
- [Grouping of fields](https://www.elastic.co/guide/en/elasticsearch/guide/current/top-hits.html)
