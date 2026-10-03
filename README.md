# mPersistence

[![GitHub Release](https://img.shields.io/github/v/release/mlynek496/mPersistence?style=for-the-badge&label=github%20release)](https://github.com/mlynek496/mPersistence/releases/latest)
[![JitPack](https://img.shields.io/jitpack/v/github/mlynek496/mPersistence?style=for-the-badge&label=JitPack)](https://jitpack.io/#mlynek496/mPersistence)
![Java](https://img.shields.io/badge/java-21%2B-orange?style=for-the-badge)

Lightweight persistence library for Java — store Java entities in SQLite, MySQL, or MongoDB through a unified Repository
API.

---

## Features

- **Write Once, Run Anywhere**: Use the same `Repository<T>` API with SQLite, MySQL, or MongoDB.
- **Repository Pattern**: Generic `Repository<T>` for saving, loading, updating, querying, and deleting entities.
- **Annotation-Driven**: Simple `@Entity` and `@Id` annotations for mapping Java classes.
- **Fluent Query API**: Filtering, sorting, pagination, and query helpers.
- **Multi-Backend Support**: SQLite, MySQL, and MongoDB.
- **Flexible IDs**: `String`, `UUID`, numeric types, MongoDB `ObjectId`, and other backend-compatible types.
- **Gson Support**: Use the default Gson configuration or provide your own.
- **Nested Fields**: Query nested values with paths such as `address.city`.

## Requirements

- **Java 21** or higher

### Supported Backends

| Backend     | Class           | Description                                           |
|:------------|:----------------|:------------------------------------------------------|
| **SQLite**  | `SQLiteBackend` | Lightweight, file-based embedded relational database. |
| **MySQL**   | `MySQLBackend`  | Production-grade relational database via JDBC.        |
| **MongoDB** | `MongoBackend`  | MongoDB document backend.                             |

---

## Installation

### Maven

Add JitPack to your `<repositories>` block in `pom.xml`:

```xml

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add the dependency:

```xml

<dependencies>
    <dependency>
        <groupId>com.github.mlynek496</groupId>
        <artifactId>mPersistence</artifactId>
        <version>1.0.9</version>
    </dependency>
</dependencies>
```

### Gradle (Groovy)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.mlynek496:mPersistence:1.0.9'
}
```

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.mlynek496:mPersistence:1.0.9")
}
```

---

## Quick Start

### 1. Define Your Entity

Annotate your class with `@Entity` and mark exactly one field with `@Id`:

```java
package pl.example.model;

import pl.persistence.annotation.Entity;
import pl.persistence.annotation.Id;

import java.util.UUID;

@Entity("users")
public class User {

    @Id
    private UUID id;

    private String username;
    private int level;

    public User() {
    }

    public User(UUID id, String username, int level) {
        this.id = id;
        this.username = username;
        this.level = level;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public int getLevel() {
        return level;
    }
}
```

Every persistent entity must contain exactly one `@Id` field.

The ID must be set before `save()`. mPersistence does not generate IDs automatically.

---

### 2. Initialize Database & Repository

#### SQLite

```java
import pl.persistence.Database;
import pl.persistence.Repository;
import pl.persistence.backend.SQLiteBackend;

import java.nio.file.Path;
import java.util.UUID;

try(Database database = new Database(
        new SQLiteBackend(Path.of("data.db"))
)){
Repository<User> users = database.repository(User.class);

    users.

save(new User(
        UUID.randomUUID(),
            "Alice",
                    42
                    ));
                    }
```

#### MySQL

```java
try(Database database = new Database(
        new MySQLBackend(
                "localhost",
                3306,
                "mydb",
                "user",
                "password"
        )
)){
Repository<User> users = database.repository(User.class);

    users.

save(new User(
        UUID.randomUUID(),
            "Alice",
                    42
                    ));
                    }
```

#### MongoDB

```java
try(Database database = new Database(
        new MongoBackend(
                "localhost",
                27017,
                "mydb",
                "user",
                "password"
        )
)){
Repository<User> users = database.repository(User.class);

    users.

save(new User(
        UUID.randomUUID(),
            "Alice",
                    42
                    ));
                    }
```

---

### 3. Basic CRUD Operations

```java
User alice = new User(
        UUID.randomUUID(),
        "Alice",
        42
);

userRepository.

save(alice);

Optional<User> foundUser =
        userRepository.findById(alice.getId());

List<User> allUsers =
        userRepository.findAll();

alice.

setLevel(43);
userRepository.

save(alice);

boolean exists =
        userRepository.existsById(alice.getId());

userRepository.

delete(alice);
userRepository.

deleteById(alice.getId());
```

`findById()` returns `Optional<T>`.

Saving an entity with an existing ID updates that entity.

---

## Query DSL

```java
List<User> users = userRepository
        .query()
        .where("level").gte(18)
        .sort("username", SortDirection.ASCENDING)
        .limit(20)
        .list();
```

### Operators

Supported operations:

- `eq(value)`
- `ne(value)`
- `gt(value)`
- `gte(value)`
- `lt(value)`
- `lte(value)`
- `in(values)`
- `exists()`
- `isNull()`
- `isNotNull()`
- `between(lower, upper)`

Nested fields use dot notation:

```java
List<User> users = userRepository
        .query()
        .where("address.city").eq("Lublin")
        .list();
```

### Sorting

```java
userRepository
        .query()
        .

sort("username",SortDirection.ASCENDING)
        .

list();
```

```java
userRepository
        .query()
        .

sortNumber("level",SortDirection.DESCENDING)
        .

list();
```

### Pagination

```java
userRepository
        .query()
        .

offset(20)
        .

limit(10)
        .

list();
```

### Query Helpers

```java
Optional<User> first = userRepository
        .query()
        .where("username").eq("Alice")
        .first();
```

```java
long count = userRepository
        .query()
        .where("level").gte(18)
        .count();
```

```java
boolean exists = userRepository
        .query()
        .where("level").gte(18)
        .exists();
```

```java
long deleted = userRepository
        .query()
        .where("level").lt(18)
        .delete();
```

---

## IDs

Every entity needs exactly one `@Id`, and the value must be set before saving.

### String

```java

@Entity("clans")
public class Clan {

    @Id
    private String tag;

    public Clan() {
    }

    public Clan(String tag) {
        this.tag = tag;
    }
}
```

```java
clanRepository.save(new Clan("SWIR"));
```

```java
clanRepository.findById("SWIR");
```

### UUID

```java

@Id
private UUID id;
```

```java
repository.save(new User(
        UUID.randomUUID(),
        "Alice",
                42
                ));
```

### Long

```java

@Id
private Long id;
```

### MongoDB ObjectId

```java
import org.bson.types.ObjectId;

@Id
private ObjectId id;
```

mPersistence does not automatically generate an ID for any of these types.

---

## Custom Gson

```java
Gson gson = new GsonBuilder()
        .create();

Database database =
        new Database(backend, gson);
```

The same Gson configuration is used by the persistence layer and storage backends.

---

## Switching Backends

Only the backend initialization changes. Repository code stays the same.

```java
StorageBackend backend =
        new SQLiteBackend(Path.of("data.db"));
```

```java
StorageBackend backend =
        new MySQLBackend(
                "localhost",
                3306,
                "mydb",
                "user",
                "password"
        );
```

```java
StorageBackend backend =
        new MongoBackend(
                "localhost",
                27017,
                "mydb",
                "user",
                "password"
        );
```

```java
Database database = new Database(backend);
Repository<User> users = database.repository(User.class);
```

---

## Important Notes

SQLite and MySQL store entity data as JSON.

For JDBC backends, filtering and sorting are evaluated in Java rather than translated into arbitrary database-specific
JSON SQL. This keeps the common query API consistent, but makes the library better suited to entity-oriented workloads
than large analytical queries, complex joins, or database-specific reporting.

MongoDB uses native document queries for the supported operations.

---

## License

This project is licensed under the [MIT License](LICENSE).
