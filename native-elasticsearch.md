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

### Approach 3: Partial Denormalization (Grouped By Profile)

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

```curl
POST 1-partial-normalized/_doc
{
  "person_id": "2",
  "person_name": "june",
  "person_hashtags": [],
  "searchable":[
    {
      "searchable_field_id": "5",
      "searchable_field_section": "device",
      "searchable_field_name": "brand",
      "searchable_field_value": "samsung"
    },
    {
      "searchable_field_id": "6",
      "searchable_field_section": "device",
      "searchable_field_name": "os",
      "searchable_field_value": "iOS"
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
        "size": 5,
        "from": 0
      }
    }    
  }
} 
```

- We can also control the number of hits in the `inner_hits` using `size` parameter
- We can also perform pagination in `inner_hits` using `from` parameter
- Need see how scores created, based on this query, both same scores but thought exact search should
  score more

3. Update person's field(s)

- Use Elasticsearch `update` API

```curl
POST 1-partial-normalized/_update/OUR2_4YBO5cAWrBZ0YuM
{
  "doc": {
   "person_name": "john tonor" 
  }
}
```

4. Update child's field(s)

```curl
POST 1-partial-normalized/_update_by_query
{
  "query": {
    "term":{
      "person_id": 1
    }
  }, 
  "script": {
    "source": 
    """
    def target = ctx._source.searchable.find(search -> search.searchable_field_id == params.searchable_field_id); 
  if (target != null){
    target.search.search_field_value = params.searchable_field_value;
  }
    
     """,
    "params": {
      "searchable_field_id": 1,
      "searchable_field_value": "samsung s23"
    }
  }
}
```

### 3B. Partial Denormalization

1. Create index mapping

```curl 
PUT /2-partial-normalized
{
  "mappings": {
    "properties":{
      "person_id": {"type": "keyword"},
      "person_name": {"type": "text"},
      "person_description": {"type": "text"},
      "person_hashtags": {"type": "keyword"},
      "record_searchable": {
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

2. Create sample documents

```curl
POST 2-partial-normalized/_doc
{
  "person_id": "2",
  "person_name": "june",
  "person_hashtags": [],
  "record_searchable":[
    {
      "searchable_field_id": "1",
      "searchable_field_section": "identifications",
      "searchable_field_name": "street address",
      "searchable_field_value": "compassvale"
    }
  ]
}
```

```curl
POST 2-partial-normalized/_doc
{
  "person_id": "2",
  "person_name": "june",
  "person_hashtags": [],
  "record_searchable":[
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
    }
  ]
}
```

- Create another person

```curl
POST 2-partial-normalized/_doc
{
  "person_id": "1",
  "person_name": "john",
  "person_hashtags": [],
  "record_searchable":[
    {
      "searchable_field_id": "3",
      "searchable_field_section": "identifications",
      "searchable_field_name": "remarks",
      "searchable_field_value": "compassvale valley"
    }
  ]
}
```

3. Search and group by person

```curl
GET 2-partial-normalized/_search
{
  "query":{
    "nested": {
      "path": "record_searchable",
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "record_searchable.searchable_field_value": "compassvale"
              }
            }
          ]
        }
      }
    }    
  },
  "collapse": {
    "field": "person_id"
  }
} 
```

- TODO: How to expand out to show `x` number of record_searchable hits apart from using `inner_hits`
  that caused duplicates

4. Paginate by profiles with facets

```curl
GET 2-partial-normalized/_search
{
  "from": 0, // Make changes to this for paginating profiles
  "size": 1, 
  "query":{
    "nested": {
      "path": "record_searchable",
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "record_searchable.searchable_field_value": "compassvale"
              }
            }
          ]
        }
      }
    }    
  },
  "collapse": {
    "field": "person_id"
  }, 
   "aggs": {
    "group_count": {
      "terms": {
        "field": "person_id"
        }
      },
    "record_searchable":{
      "nested": {
        "path": "record_searchable"
      },
      "aggs": {
        "Field Name Filter": {
          "terms": {
            "field": "record_searchable.searchable_field_name",
            "size": 10
            }
          }
        }
      }
    }
}
```

5. Paginate by `record_searchable` within the same set of profiles

```curl
GET 2-partial-normalized/_search
{
  "_source": {
    "excludes": ["record_searchable.*"]
  },
  "from": 0,  
  "query":{
    "nested": {
      "path": "record_searchable",
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "record_searchable.searchable_field_value": "compassvale"
              }
            }
          ]
        }
      },
       "inner_hits": {
         "size": 1, 
         "from": 1 // Make changes to this to paginate searchable hits
      }
       
    }    
  },
  "collapse": {
    "field": "person_id"
    
  }, 
   "aggs": {
    "person_id": {
      "terms": {
        "field": "person_id"
        }
      },
    "record_searchable":{
      "nested": {
        "path": "record_searchable"
      },
      "aggs": {
        "Field Name Filter": {
          "terms": {
            "field": "record_searchable.searchable_field_name",
            "size": 10
            }
          }
        }
      }
    }
} 
```

7. Set the number of `record_searchable` hits within a profile & a record

```curl
GET 2-partial-normalized/_search
{
  "_source": {
    "excludes": ["record_searchable.*"]
  },
  "from": 0, 
  "query":{
    "nested": {
      "path": "record_searchable",
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "record_searchable.searchable_field_value": "compassvale"
              }
            }
          ]
        }
      },
       "inner_hits": {
         "size": 1
      }
       
    }    
  },
  "collapse": {
    "field": "person_id"
  }, 
   "aggs": {
    "person_id": {
      "terms": {
        "field": "person_id"
        }
      },
    "record_searchable":{
      "nested": {
        "path": "record_searchable"
      },
      "aggs": {
        "Field Name Filter": {
          "terms": {
            "field": "record_searchable.searchable_field_name",
            "size": 10
            }
          }
        }
      }
    }
} 
```

8. Set the number of searchable records per profile & per record

```curl
GET 2-partial-normalized/_search
{
 "_source": {
    "excludes": ["record_searchable.*"]
  },
  "from": 0,  
  "size": 10, 
  "query":{
    "nested": {
      "path": "record_searchable",
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "record_searchable.searchable_field_value": "compassvale"
              }
            }
          ]
        }
      },
    "inner_hits": {
         "size": 1, // sets how many searchable fields per record to return (i.e. number of nested elements to return in a document)
         "from": 0,
          "sort": {"_score": {"order": "asc"}}
      }
    }    
  },
  "collapse": {
    "field": "person_id",
     "inner_hits": {
      "name": "record_hits", // set how many records per profile to return (i.e. number of documents grouped by profile to return )
      "size": 3,
      "sort": [{"_score": {"order": "asc"}}]
    }
  }
}
```

9. Add a new searchable field to existing profile and record

```curl
POST 2-partial-normalized/_update/0urRQIcBG-0whW4Gf0G4
{
  "script": {
    "source": "ctx._source.record_searchable.add(params.searchable)",
    "params": {
      "searchable": {
        "searchable_field_id": "6",
        "searchable_field_section": "identifications",
        "searchable_field_name": "remarks",
        "searchable_field_value": "just opposite compassvale secondary school"
      }
    }
  }
}
```

10. Remove a searchable field

```curl
POST 2-partial-normalized/_update/0urRQIcBG-0whW4Gf0G4
{
  "script": {
    "source": "ctx._source.record_searchable.removeIf(record -> record.searchable_field_id == params.searchable_field_id)",
    "params": {
      "searchable_field_id": "65"
    }
  }
} 
```

11. [Extra] Sort across all `searchable fields` and return top 5 search results (regardless of which
    profile and which record document)

```curl
GET 2-partial-normalized/_search
{
  "from": 0,  
  "size": 10, 
  "sort": [
    {
      "_score": {
        "order": "desc"
      }
    }
  ],
  "query":{
    "nested": {
      "path": "record_searchable",
      "query": {
        "bool": {
          "must": [
            {
              "match": {
                "record_searchable.searchable_field_value": "compassvale"
              }
            }
          ]
        }
      }
    }
  },
  "collapse": {
    "field": "person_id"
  },
  "aggs":{
     "records": {
          "nested": {
            "path": "record_searchable"
          },
          "aggs": {
            "top_searchable":{
            "top_hits": {
              "sort": [
                {
                  "_score": {
                    "order": "desc"
                  }
                }
              ],
              "size": 5
            }
          }}
        }
  }
}
```
