# Audit and Traceability Package

This package provides auditing functionality for cohort, patient, and schema data changes using Hibernate Envers.

## Overview

Whenever data is modified through HTTP endpoints or WebSocket bulk imports, an AudixContext 
is populated with meta information and envers on any database operation then uses the meta 
information to create to fill the auditing tables.

## Implementation Details

### HTTP Endpoints
- A filter sets the audit context for all HTTP requests that modify data (RequestScoped)
- The Envers revision listener accesses this context to populate audit information (RequestScoped)

### WebSocket Bulk Import
- The audit context is manually injected and modified during bulk operations (Also RequestScoped, supported by the quarkus next websocket implementation)

### Dependency Injection
- Uses Quarkus ArC instead of standard CDI in the user listener
- Standard `@Inject AuditContext context;` was not working at all, it always injected null.

## Envers Configuration

The package leverages Hibernate Envers' collection change tracking:

- **Setting**: `org.hibernate.envers.revision_on_collection_change` (default: `true`)
- **Behavior**: When a collection in an entity changes, a new revision is automatically created for the parent entity
- **Example**: Changes to a `OneToMany` collection trigger a new revision for `PatientMetaEntity`, even when the mapping is on the "many" side

For more details, see the [Hibernate User Guide](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#envers-configuration).
This means that even when a PatientDataEntry changes, this also triggers a new Revision for the MetaPatientEntity.
