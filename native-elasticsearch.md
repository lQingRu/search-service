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

- Will need to use an additional `filters: {<search filter>}` if we want to return facets only
  matching query

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

### Approach 3: Partial Normalization

1. Create index

```curl
PUT /1-partial-normalized
{
  "mappings": {
    "properties":{
      "person_id": {"type": "keyword"},
      "person_name": {"type": "text"},
      "person_description": {"type": "text"},
      "person_hashtags": {"type": "keyword"},
      "searchable": {
        "type": "nested",
        "properties": {
           "searchable_field_id": {"type": "keyword"},
            "searchable_field_section": {"type": "keyword"},
            "searchable_field_name":  {"type": "keyword"},
            "searchable_field_value":  {"type": "text"}
        }
      }
    }
  }
}
```

2. Create document

```curl
POST 1-partial-normalized/_doc
{
  "person_id": "1",
  "person_name": "john doe",
  "person_description": "this is a test denormalized document descr",
  "person_hashtags": ["tag1", "tag2"],
  "searchable":[
    {
      "searchable_field_id": "1",
      "searchable_field_section": "device",
      "searchable_field_name": "brand",
      "searchable_field_value": "samsung"
    },
    {
      "searchable_field_id": "2",
      "searchable_field_section": "device",
      "searchable_field_name": "os",
      "searchable_field_value": "iOS"
    },
    {
      "searchable_field_id": "1",
      "searchable_field_section": "device",
      "searchable_field_name": "brand",
      "searchable_field_value": "samsung 23"
    }
  ]
  
}

```

3. Query on all searchable field values of `samsung`

```curl
GET 1-partial-normalized/_search
{
  "query":{
    "nested": {
      "path": "searchable",
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "searchable.searchable_field_value": "samsung"
              }
            }
          ]
        }
      }
    }    
  }
}
```

- This returns the whole document even though only 2 of 3 searchable fields in `searchable` array
  matches
    - There are 2 possible workarounds:

1. Add `inner_hits` where it returns only matched object which we can perform in-application
   filtering ourselves

```curl
GET 1-partial-normalized/_search
{
  "query":{
    "nested": {
      "path": "searchable",
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "searchable.searchable_field_value": "samsung"
              }
            }
          ]
        }
      },
      "inner_hits": {}
    }    
  }
}
```

2. Exclude all the nested objects fields in `_source` and only use return results from `inner_hits`
   to enrich search results

- This would be especially useful when we expect growing `searchable` nested objects

```curl
GET 1-partial-normalized/_search
{
  "_source": {
    "excludes": ["searchable.*"]
  }
  , 
  "query":{
    "nested": {
      "path": "searchable",
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "searchable.searchable_field_value": "samsung"
              }
            }
          ]
        }
      },
      "inner_hits": {
        "size": 1,
        "from": 1
      }
    }    
  }
} 
```

- We can also control the number of hits in the `inner_hits` using `size` parameter
- We can also perform pagination in `inner_hits` using `from` parameter

