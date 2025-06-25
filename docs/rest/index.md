# Implementing REST Connector

## Initial Steps in General

1. Get the documentation for service / application for which connector is written.
2. Determine connection & authentication parameters
3. Implement test connection
4. Determine an list of objects & concepts for identity & access management
5. Implement search support for User / account object class
   1. Get or Write schema for the object class
   2. Determine API endpoint for the object class
   3. Implement basic search handler for the object class
   4. Implement support for getting user by `UID` (search by filter) 
   5. (optional) implement advanced filtering support for search
6. Implement search support for other object classes (same steps as for first class)
7. Based on application and documentation determine relations (associations) between supported object classes
8. Implement schema for relations (associations)
9. Implement fetching related objects (associations)

## Additional tasks / steps

* Implement create operation for the object class
* Implement update operation for the object class
* Implement configuration discovery
