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

To do incremental evaluation, we consider the scenarios in order:

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

# High-level comparison of general approaches for entity relationships

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
- Each nested object is indexed as a separate Lucene document i.e. if we have `10` nested objects,
  there will be `11` documents created
    - Because of the expense associated with `nested` mappings, ES has settings that we can
      configure to guard against performance problems (but we will need to then have workaround):
        - `index.mapping.nested_fields.limit`: maximum number of distinct nested mappings in an
          index. Default = 50
        - `index.mapping.nested_objects.limit`: maximum number of nested JSON objects that a single
          document can contain across all nested types. Default = 10000
            - Limit prevents out of memory error when document contains too many nested objects
- To make changes to nested objects, would need to either:
    - Partial update API
    - Script
      update (https://iridakos.com/programming/2019/05/02/add-update-delete-elasticsearch-nested-objects)
        - Script update allows flexibility, but wary of maintainability and complexity

### Pros

- Fields within the object maintain their relationship
- Because of the way nested objects are indexed, joining the nested documents to the root document
  at query time is fast (as though it is a single document)
    - `nested` query and filter provide fast query-time joins
- Performant even with deeply nested objects

### Cons

- These nested documents are hidden, i.e. we cannot access/query them directly
    - Will need to use `nested query` or `nested filters`
    - Complex queries because a special nested query needs to be constructed for search,
      aggregation, and highlighting
- Results returned is the whole document
- Limited to a maximum of 10,000 nested objects per document

### Conclusion

- "Cross-referencing" nested documents is not feasible
    - Workaround is to use `include_in_root` that copies the properties from the nested documents
      into the roots, BUT brings up issues with inner objects (with no boundaries)
        - BUT this means that all fields in the nested objects are indexed twice
- Useful if there is one main entity with a limited number of closely related objects (nested
  objects)

## Approach 4 - Parent-Child model: One-to-Many relationships

- Similar in nature to the `nested objects model`, but parent and child are completely different
  documents
- Requires 2 additional "metadata":
    - Field with `join` data type
    - Extra details about relationship using `relations` object
- Parent ID needs to be specified when indexing child document because all documents inside a
  parent-child relationship must be in the same shard
    - Parent ID serves as a routing key

### Pros

- Indexing parents is no different from any other documents
    - Parents do not need to know about their children, can index separately
    - Hence fast indexing time
- Child documents can be changed without affecting the parent or other children
- Child documents can be returned as a result of a search request

### Cons

- Parent and all of its children must reside in the same shard and same index
    - Routing key needs to be provided when retrieval, deletion, update of child documents
- Queries are more expensive and memory-intensive (than nested equivalents)
    - Each `relation` level adds overhead in terms of processing and memory
    - Memory needed to keep the mapping of parent and children
- The more joins we have, the worse performance will be
    - Each generation of parents needs to have their string `_id` fields stored in memory, which
      consume a lot of RAM
        - Good to keep `_id` small
- Can be complex when querying across multiple levels of hierarchy or when there is a deeply nested
  parent-child relationships
- Only one `join` field mapping is allowed per index
- Cannot specify mappings for `parent` and `child` separately, can only specify the whole index
- Control over sorting and scoring is limited
    - Need to use `function_score` and sort by `_score` if we want to sort according to a field in
      child or parent documents
- **Can only filter on parent content or child content, BUT not both**

### Conclusion

- Useful if there are a lot more children than more parents and documents need to be added or
  changed frequently
- Useful if index performance is important
- Multiple levels of parent & child is possible BUT not recommended because of the overhead for
  creating joins for multiple layers
- A lot of uncertainties:
    - Is parent and children document treated the same when scoring if we use a default query to
      search across parent & children documents?
    - Are we able to set the number of documents to be returned for children documents?
    - Seems like unable to search against parent & children filters
        - Potentially a dealbreaker

# POC for Global Search

## Must-have features for evaluation

- Search
    - Base search
        - Simple string query without filters
    - Base search & static filter
        - Static filter here would then refer to category of search (could be a parent or child
          filter)
    - Base search & dynamic filters
        - Dynamic filter: Parent and/or Child
- Access control
    - Search
    - Facets
    - Sort
- Sort
    - First-level sort
        - Parent or child
- Pagination
    - Parent (total number of persons)
    - Child (total number of hits)
- Group by Person
- Highlighting
    - Parent
    - Child

## Devil's Advocate: Maintainability

- Search result not grouped by person?
    - What would be the chance of this?
- Update person's information
    - Transfer person to another team
    - Update field values
- Transfer device to another person

## Candidates

### 1. Denormalized

- Single flattened index: `denormalized`
- 1 Document : 1 Searchable Field

### 2. Partial denormalized

- Single grouped index: `partial-denormalized`
- Possible groupings:
    - Group by record
        - Aligns with ACL
        - Helps with growing size
        - Mitigation on data duplication
        - 1 Document: 1 Record : `x` Searchable Fields
    - Group by person
        - May be too hard to scale
        - No data duplication
        - 1 Document: 1 Person: `n` Records: `z` Searchable Fields

### 3. Parent-child model

- Single independent index: `parent-child`
    - Parent as a single document, Child as a single document

### 4. Normalized

- At least 2 indexes: `person` (container/parent), `all` (searchable fields)

## Summary

- Not accounting for access control here yet

### Search [Functional]

| Approach | Base Search | Search & Static Filter                                                                                                                                 | Search & Dynamic Filter | 
| --- |-------------|--------------------------------------------------------------------------------------------------------------------------------------------------------| --- |
| (1) Denormalized | ✅           | ✅                                                                                                                                                      | ✅ | 
| (2) Partial denormalized <br/> - Independent of groupings | ✅           | ✅                                                                                                                                                      | ✅ | 
| (3) Parent-child model | ✅           | ❌ <br/> - Does not work if mix of parent & child filters <br/> - But potentially, can have workaround for static filters to be in parent document only | ❌ <br/> - Does not work if mix of parent & child filters | 
| (4) Normalized | ✅ | ✅ | ✅ |

### Search [Non-functional]

| Approach | Base Search                                                                                                                             | Search & Static Filter                                                                            | Search & Dynamic Filters         | 
| --- |-----------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|----------------------------------|
| (1) Denormalized | Simple, all first level                                                                                                                 | Additional `filter` parameter(s)                                                                  | No difference from static filter |
| (2) Partial denormalized | - Requires "`x2`" (depends if ES uses cache well) query for matching nested documents <br/> - Fast query-time joins with `nested query` | Additional `nested` and/or `filter` parameter(s)                                                  | No difference from static filter
| (3) Parent-child model | More expensive and higher memory intensive than (1) and (2)                                                                             | Additional `filter` parameter(s) in `has_child` or `has_parent` query                             | No difference from static filter |
| (4) Normalized | Requires in-application joins. <br/> Should be more expensive than (1) and (2)                                                          |  Additional `filter` parameter(s). <br/> - May even be more expensive than (3) depending on the number of calls needed <br/> - Minimally 2 calls for retrieval of parent & child, additional calls needed to fill the "buckets" <br/> - Higher chances that require >2 calls to fill "buckets" | No difference from static filter |

### Sort

| Approach | Parent sort | Child sort | Multiple sorts | Remarks                                                      | 
| --- | --- | --- | --- |--------------------------------------------------------------| 
| (1) Denormalized | ✅ | ✅ | ✅ | No difference since flattened. Should be the most performant |
| (2) Partial denormalized | ✅ | ✅ | ✅ | Child sort will be expensive (??)                            |
| (3) Parent-child model | ✅ | ✅ | ❌ | Requires custom script (`function_sort`) for sorting         |
| (4) Normalized | ✅ | ✅ | ✅ | Child to be run before parent | 

### Indexing

| Approach                 | Create Searchable                                                                                                                        | Update Person                                         | Update Searchable                                                             | Delete Person                              | Delete Searchable    |
|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|-------------------------------------------------------------------------------|--------------------------------------------|---|
| (1) Denormalized         | 💸💸 <br/> In-app enrichment of parent before indexing                                                                                   | 💸💸💸 <br/> Propagate to all children documents      | 💸 <br/> Only a single document affected                                      | 💸💸💸 <br/> All children documents affected | 💸 <br/> Only a single document affected | 
| (2) Partial-denormalized | 💸 (Subsequent children)  <br/> or  <br/> 💸💸 (First child) <br/> - Caveat: `id` of document is custom (i.e. either `personId` or `recordId`) | 💸 (Group by person)  <br/> or  <br/>💸💸 (Group by record) | 💸 <br/> KIV: May need to verify processing & maintainbility of custom script | 💸 (Group by person) <br/> or  <br/>💸💸 (Group by record) | 💸 <br/> KIV: May need to verify processing & maintainbility of custom script|     
| (3) Parent-child model   | 💸 <br/> Only a single document affected                                                                                                 | 💸   <br/> or <br/> 💸💸💸 (If merge person)          | 💸  <br/>or  <br/>💸💸💸 (If transfer entity to another person)                           |  💸💸💸 <br/> Propagate to all children documents |  💸 <br/> Only a single document affected|
| (4) Normalized           | 💸 <br/> Only a single document affected                                                                                                 | 💸  <br/> Only a single document affected             | 💸 <br/> Only a single document affected |  💸💸💸 <br/> All children documents affected | 💸  <br/> Only a single document affected             | 

# Deeper-dive into Partial Denormalized vs Fully Denormalized

- Partial here will be grouped by record instead of group by profile as nested fields are stored in
  the same document (just hidden documents) as the parent, which means a growing size of document

## Core Functionalities

### Search

| Feature | Partial                                                      | Full              | 
| --- |--------------------------------------------------------------|-------------------| 
| Group by Profile | `collapse` function                                          | Same as `Partial` |
| Paginate by Profile | `collapse` does not provide out-of-box distinct group sizes* | Same as `Partial` |
| Paginate by Search Hits | `collapse`'s `inner_hits.size`                               | Same as `Partial`  |

#### *Paginate by Profile

1. Obtain number of unique groups through aggregations
2. Update page number, `from`, when `total num of unique groups` > `x+1` * `pageSize` where `x` is
   current page

### Sorting

| Feature                                                              | Partial                                                                                                                                                                                                                           | Full                                     | 
|----------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------|
| Sort by Profile fields                                               | `sort` parameter, flexible                                                                                                                                                                                                        | Same as `Partial`                        |
| Sort by Child fields or Relevance scoring (`searchable_field_value`) | Complex if partial denormalization is not grouped by profile. <br/> - Not able to perform centralized scoring across all documents within the same profile easily (would need to do in-app comparison between returned documents) | Easy since flattened to just `inner_hits` |

### Add, Change, Delete

| Feature                             | Partial                                                                                                                                     | Full                                              | 
|-------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------|
| Changes to searchable field content | 💸💸💸 <br/> To add, change, or delete a nested document, the whole document must be reindexed.* <br/> More nested documents = More costly. | 💸 <br/> Only changes the document affected |
| Changes to root field content       | 💸💸 <br/> Depending on how many records a profile have since grouped by record                                                             | 💸💸💸 <br/> To propagate changes down to all affected documents |

#### *Change to searchable field content in Nested Data Model

- Table above is in the case of when we use a typical `UPDATE` API by ES which requires a full
  reindex of document
- However, if we were to apply partial update, i.e. through scripting, we only require to update the
  affected nested field

### Access Control

- Assumption here: role-based access control
- Fields that are needed for evaluation, will need to be tag to each document to simplify search

### Facets

- Does not come out-of-the-box, will require the use of `aggregations`
- Even with `aggregations`, it will only return based on search hits and not global

| Partial | Full |
| --- | --- | 
| Require `nested` and root aggregations | Just `root` aggregations | 

### Advanced / Possible Functionalities

| Feature | Partial              | Full                                                                                                                                                                                     | 
| --- |----------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| 
| Flexibility of search counts per record | 1st level `collapse` | Unless `record` identifier saved in each document. <br/> Still possible if save a field with the combination of `profileId-recordSectionName-recordId` and then `collapse` by this field |

### Conclusion

- Decided to go for `Fully Denormalized` because:
    - `Partial Denormalized` is down to just `group by record / a level down of profile`
      because `profile` itself will result rather unbounded list of `searchable fields`
        - Higher risk of having concurrency issues as well
    - Given that we are looking at `Partial Denormalized` being at `record` level, the main
      advantages from it over `Fully Denormalized` is that we don't need to propagate as much when
      there are profile changes
        - But we need to also see how frequent these changes are and how much are the overhead
          actually
        - Think we shouldn't also do premature optimizations at the expense of complexity and
          inflexibility
    - `Partial Denormalized` though may be useful in the future where we may want to group search
      hits by `record` which may then be quite easily done through `collapsing` and reading from
      different leveled documents that are already stored in `record` level
        - But `Fully Denormalized` would likely still be able to achieve through `nested collapsing`
    - `Partial Denormalized` makes it complex to sort documents within a profile
        - Given that out-of-the-box, after `collapse` by profile, `record document` are sorted
          within a profile based on its top hit
            - However, we are currently interested in just 1st level, i.e. documents within
              profile (not segregating between records within profile)
            - But if next time there is a case for this, `Fully Denormalized` would
              require `nested collapsing` which is still doable
    - `Partial Denormalized` requires additional configuration to prevent duplicated data (such as
      from `inner_hits`, `collapse.inner_hits` and `_source`) which were generated because of the
      need to specify sorting and size for `nested objects` and `within grouped`
        - This results in additional complexity as well as less readable and maintainable ES APIs
          used
    - Because of `nested data structure` in `Partial Denormalized`, it also makes it difficult to
      perform changes to searchable fields
        - Though both require `Partial Update API`, in `Partial Denormalized` model, we will need to
          loop through all nested objects to find the exact object to update
    - In terms of filtering, sorting, and generating of facets, there are additional complexities
      with use of `Partial Denormalized`
        - We will always need to perform at least 2 levels (i.e. root and nested) which are
          different APIs
    - Additional storage is also required for storing `nested data structures`

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

## Continue

- Multi-level: Profile > Comms Info > Sel
- Collapse
    - "The total number of `hits` in the response indicates the number of matching documents without
      collapsing. The total number of distinct group is unknown."

## Indexing

### Denormalized

- M1: Retrieve all affected beans from Mongo
    - Index all based on mongo
- M2: Update by query

### Partial-normalized

- Versioning?
- Expanding out nested objects after `collapse` for records

## Resources

- [Join queries](https://www.elastic.co/guide/en/elasticsearch/reference/current/joining-queries.html)
- [Handling relationships](https://www.elastic.co/guide/en/elasticsearch/guide/current/relations.html)
- [Grouping of fields](https://www.elastic.co/guide/en/elasticsearch/guide/current/top-hits.html)
- [Model relationship using nested objects](https://opster.com/guides/elasticsearch/data-architecture/how-to-model-relationships-between-documents-in-elasticsearch-using-nesting/)

