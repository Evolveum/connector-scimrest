# Indirect Resolution

## Problem Statement

Some applications does not have endpoints to list all objects of some type, or all related objects.
Custom Groovy implementations could handle this issue, but pose security risk. 
Ideally we should be aim to design non-script handling for such common cases.

## Solution Proposals

### Simple Pipeline inspired by Java Stream API

```groovy
objectClass("Team") {
    search {
        custom {
            emptyFilterSupported(true)
        }
        implementation {
            
        }
    }
}
```