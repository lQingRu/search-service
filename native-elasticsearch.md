# Simple Use Case

## 1. Parent & Searchable Field

### Approach 1: Denormalized

1. Create index

```curl
PUT /1-denormalized
{
  "mappings": {
    "properties":{
      "person_id": {"type": "keyword"},
      "person_name": {"type": "text"},
      "person_description": {"type": "text"},
      "person_hashtags": {"type": "keyword"},
      "searchable_field_id": {"type": "keyword"},
      "searchable_field_section": {"type": "keyword"},
      "searchable_field_name":  {"type": "keyword"},
      "searchable_field_value":  {"type": "text"}
    }
  }
}
```

2. Index a sample document

- TODO: When do we want to embed as an object?
- TODO: What's the unique identifier for a document?
    - `<section_name>_<section_id>_<field_name>`

```curl
POST 1-denormalized/_doc
{
  "person_id": "1",
  "person_name": "john doe",
  "person_description": "this is a test denormalized document descr",
  "person_hashtags": ["tag1", "tag2"],
  "searchable_field_id": "1",
  "searchable_field_section": "device",
  "searchable_field_name": "brand",
  "searchable_field_value": "samsung"
}
```

```curl
POST 1-denormalized/_doc
{
  "person_id": "1",
  "person_name": "john doe",
  "person_description": "this is a test denormalized document descr",
  "person_hashtags": ["tag1", "tag2"],
  "searchable_field_id": "1",
  "searchable_field_section": "device",
  "searchable_field_name": "os",
  "searchable_field_value": "iOS"
}
```

```curl
POST 1-denormalized/_doc
{
  "person_id": "2",
  "person_name": "jenny",
  "searchable_field_id": "2",
  "searchable_field_section": "device",
  "searchable_field_name": "os",
  "searchable_field_value": "iOS"
}
```

3. Check that document is indexed correctly

```curl
GET 1-denormalized/_search
{
  "query":{
    "match_all": {}
  }
}
```

4. Query by a parent field (as though * & parent filter)

```curl
GET 1-denormalized/_search
{
  "query":{
    "match": {
      "person_description": {
        "query": "descr"
      }
    }
  },
  "collapse": {
    "field": "person_id",
    "inner_hits": {
      "name": "most_recent",
      "size": 5
    }
  }
}
```

5. Query by searchable field section and return facets

- Group by `person_id`

```curl
GET 1-denormalized/_search
{
  "query":{
    "match": {
      "searchable_field_section": {
        "query": "device"
      }
    }
  },
  "collapse": {
    "field": "person_id",
    "inner_hits": {
      "name": "most_recent",
      "size": 5
    }
  },
  "aggs": {
    "Field Name Filter": {
      "terms": {
        "field": "searchable_field_name",
        "size": 10
      }
    }
  }
}
```

### Approach 2: Parent-child model

1. Create index

```curl
PUT /1-parentchild
{
  "mappings": {
    "properties": {
      "join_field": {
        "type": "join",
        "relations": {
          "person": "searchable"
        }
      },
      "person_id": {"type": "keyword"},
      "person_name": {"type": "text"},
      "person_description": {"type": "text"},
      "person_hashtags": {"type": "keyword"},
      "searchable_field_id": {"type": "keyword"},
      "searchable_field_section": {"type": "keyword"},
      "searchable_field_name":  {"type": "keyword"},
      "searchable_field_value":  {"type": "text"}
    }
  }
}
```

2. Index the parent document

```curl
POST /1-parentchild/_doc
{
  "person_id": "1",
  "person_name": "john doe",
  "person_description": "this is a test denormalized document descr",
  "person_hashtags": ["tag1", "tag2"],
  "join_field": {
    "name": "person"
  }
}
```

3. Index the children document

```curl
POST /1-parentchild/_doc?routing=Y0qR6oYB4yZiC9mA6HKD
{
  "searchable_field_id": "1",
  "searchable_field_section": "device",
  "searchable_field_name": "brand",
  "searchable_field_value": "samsung",
  "join_field": {
    "name": "searchable",
    "parent": "Y0qR6oYB4yZiC9mA6HKD"
  }
}
```

- ES will throw error if the children name is not found in index
- ES will throw error if `routing` is not specified when indexing

4. Search against a child field and return the parent document (using `inner_hits`)

```curl
GET /1-parentchild/_search
{
  "query":{
    "has_parent": {
      "parent_type": "person",
      "query": {
        "match": {
          "person_name":  "john doe"
        }
      },
      "inner_hits": {}
    }
  }
}
```

5. Search against child and parent fields

```curl
GET /1-parentchild/_search
{
  "query":{
    "bool": {
      "must": [
        {
           "has_parent": {
              "parent_type": "person",
              "query": {
                "match": {
                  "person_name":  "john doe"
                }
              },
              "inner_hits": {}
            }
        },
         {
           "has_child": {
              "type": "searchable",
              "query": {
                "match": {
                  "searchable_field_name":  "brand"
                }
              }
            }
        }
        
        
        ]
    }
   
  }
}
```

- Does not work


