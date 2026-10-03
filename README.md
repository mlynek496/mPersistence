# mPersistence

[![GitHub Release](https://img.shields.io/github/v/release/mlynek496/mPersistence?style=for-the-badge&label=github%20release)](https://github.com/mlynek496/mPersistence/releases/latest)
[![JitPack](https://img.shields.io/jitpack/v/github/mlynek496/mPersistence?style=for-the-badge&label=JitPack)](https://jitpack.io/#mlynek496/mPersistence)
![Java](https://img.shields.io/badge/java-21%2B-orange?style=for-the-badge)

---

## Features

- **Write Once, Run Anywhere**: Swap storage backends with a single line of code without touching your business logic.
- **Repository Pattern**: Generic `Repository<T>` interface providing clean CRUD and query operations.
- **Annotation-Driven**: Simple `@Entity` and `@Id` annotations to map POJOs.
- **Fluent Query DSL**: Dynamic filtering (`Filter`, `Operator`) and sorting (`Sort`, `SortDirection`) across all backends.
- **Multi-Backend Support**: Embedded SQLite, MySQL, and MongoDB out of the box.
- **Zero Overhead**: Clean, lightweight architecture designed for fast execution.

## Requirements

- **Java 21** or higher

### Supported Backends

| Backend | Class | Description |
| :--- | :---  | :--- |
| **SQLite** | `SQLiteBackend`  | Lightweight, file-based embedded relational database. |
| **MySQL** | `MySQLBackend`  | Production-grade relational database via JDBC. |
| **MongoDB** | `MongoBackend` | High-performance NoSQL document store. |

---
Installation
Maven
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
<dependency>
    <groupId>com.github.mlynek496</groupId>
    <artifactId>mPersistence</artifactId>
    <version>1{version}</version>
</dependency>
```
Gradle (Groovy)
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.mlynek496:mPersistence:{version}'
}
```
Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.mlynek496:mPersistence:{version}")
}
```
JitPack
You can also use the same GitHub release/tag directly through JitPack:
```text
com.github.mlynek496:mPersistence:{version}
```
---
Quick Start
1. Define Your Entity
Annotate your class with `@Entity` and mark exactly one field with `@Id`.
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

    public void setLevel(int level) {
        this.level = level;
    }
}
```
Every persistent entity must contain exactly one `@Id` field.
The ID must be assigned before calling `save()`.
mPersistence does not automatically generate IDs.
Supported ID types depend on the selected backend and serializer. Common examples include:
```java
@Id
private String id;
```
```java
@Id
private UUID id;
```
```java
@Id
private Long id;
```
```java
@Id
private Integer id;
```
For MongoDB, Mongo-specific types such as `ObjectId` can also be used:
```java
import org.bson.types.ObjectId;

@Id
private ObjectId id;
```
---
2. Initialize Database & Repository
Create a `StorageBackend` instance, wrap it in `Database`, and fetch the `Repository`.
SQLite
```java
import pl.persistence.Database;
import pl.persistence.Repository;
import pl.persistence.backend.SQLiteBackend;

import java.nio.file.Path;
import java.util.UUID;

public class Main {

    public static void main(String[] args) {
        try (Database database = new Database(
                new SQLiteBackend(Path.of("data.db"))
        )) {
            Repository<User> userRepository =
                    database.repository(User.class);

            User alice = new User(
                    UUID.randomUUID(),
                    "Alice",
                    42
            );

            userRepository.save(alice);
        }
    }
}
```
MySQL
```java
import pl.persistence.Database;
import pl.persistence.Repository;
import pl.persistence.backend.MySQLBackend;

import java.util.UUID;

public class Main {

    public static void main(String[] args) {
        try (Database database = new Database(
                new MySQLBackend(
                        "localhost",
                        3306,
                        "mydb",
                        "user",
                        "password"
                )
        )) {
            Repository<User> userRepository =
                    database.repository(User.class);

            User alice = new User(
                    UUID.randomUUID(),
                    "Alice",
                    42
            );

            userRepository.save(alice);
        }
    }
}
```
MongoDB
```java
import pl.persistence.Database;
import pl.persistence.Repository;
import pl.persistence.backend.MongoBackend;

import java.util.UUID;

public class Main {

    public static void main(String[] args) {
        try (Database database = new Database(
                new MongoBackend(
                        "localhost",
                        27017,
                        "mydb",
                        "user",
                        "password"
                )
        )) {
            Repository<User> userRepository =
                    database.repository(User.class);

            User alice = new User(
                    UUID.randomUUID(),
                    "Alice",
                    42
            );

            userRepository.save(alice);
        }
    }
}
```
---
3. Basic CRUD Operations
```java
// Create & Save
User alice = new User(
        UUID.randomUUID(),
        "Alice",
        42
);

userRepository.save(alice);

// Find by ID
Optional<User> foundUser =
        userRepository.findById(alice.getId());

// Fetch All
List<User> allUsers =
        userRepository.findAll();

// Update
alice.setLevel(43);
userRepository.save(alice);

// Check if entity exists
boolean exists =
        userRepository.existsById(alice.getId());

// Delete
userRepository.delete(alice);

// Delete by ID
userRepository.deleteById(alice.getId());
```
`findById()` returns `Optional<T>`.
Calling `save()` with an existing ID updates that entity.
---
Query DSL
Build queries directly from `Repository<T>`:
```java
List<User> filteredUsers = userRepository
        .query()
        .where("level").gt(10)
        .sort("username", SortDirection.ASCENDING)
        .list();
```
Operators
Supported operations:
`eq(value)`
`ne(value)`
`gt(value)`
`gte(value)`
`lt(value)`
`lte(value)`
`in(values)`
`exists()`
`isNull()`
`isNotNull()`
`between(lower, upper)`
Equal
```java
List<User> users = userRepository
        .query()
        .where("username").eq("Alice")
        .list();
```
Not Equal
```java
List<User> users = userRepository
        .query()
        .where("username").ne("Alice")
        .list();
```
Greater Than
```java
List<User> users = userRepository
        .query()
        .where("level").gt(10)
        .list();
```
Greater Than or Equal
```java
List<User> users = userRepository
        .query()
        .where("level").gte(18)
        .list();
```
Less Than
```java
List<User> users = userRepository
        .query()
        .where("level").lt(50)
        .list();
```
Less Than or Equal
```java
List<User> users = userRepository
        .query()
        .where("level").lte(50)
        .list();
```
IN
```java
List<User> users = userRepository
        .query()
        .where("username")
        .in(List.of("Alice", "Bob"))
        .list();
```
BETWEEN
```java
List<User> users = userRepository
        .query()
        .where("level")
        .between(10, 50)
        .list();
```
EXISTS
```java
List<User> users = userRepository
        .query()
        .where("email")
        .exists()
        .list();
```
NULL
```java
List<User> users = userRepository
        .query()
        .where("email")
        .isNull()
        .list();
```
NOT NULL
```java
List<User> users = userRepository
        .query()
        .where("email")
        .isNotNull()
        .list();
```
Multiple Filters
```java
List<User> users = userRepository
        .query()
        .where("level").gte(18)
        .where("username").ne("Admin")
        .list();
```
Nested Fields
Nested values can be addressed using dot notation:
```java
List<User> users = userRepository
        .query()
        .where("address.city")
        .eq("Lublin")
        .list();
```
---
Sorting
Ascending
```java
List<User> users = userRepository
        .query()
        .sort("username", SortDirection.ASCENDING)
        .list();
```
Descending
```java
List<User> users = userRepository
        .query()
        .sort("username", SortDirection.DESCENDING)
        .list();
```
Numeric Sorting
```java
List<User> users = userRepository
        .query()
        .sortNumber("level", SortDirection.DESCENDING)
        .list();
```
When sorting is used, `_id` is used as a stable secondary sort when it is not already part of the sort definition.
---
Pagination
Use `offset()` and `limit()` for basic pagination:
```java
List<User> users = userRepository
        .query()
        .offset(20)
        .limit(10)
        .list();
```
---
Query Helpers
First Result
```java
Optional<User> user = userRepository
        .query()
        .where("username").eq("Alice")
        .first();
```
Count
```java
long count = userRepository
        .query()
        .where("level").gte(18)
        .count();
```
Exists
```java
boolean exists = userRepository
        .query()
        .where("level").gte(18)
        .exists();
```
Delete by Query
```java
long deleted = userRepository
        .query()
        .where("level").lt(18)
        .delete();
```
`count()`, `exists()`, and `delete()` use the query filters and do not apply sorting or pagination.
---
IDs
Every entity must have exactly one `@Id`.
The library does not generate IDs automatically. Assign the ID before saving the entity.
String ID
```java
@Entity("clans")
public class Clan {

    @Id
    private String tag;

    private String name;

    public Clan() {
    }

    public Clan(String tag, String name) {
        this.tag = tag;
        this.name = name;
    }
}
```
Save and load:
```java
Clan clan = new Clan("SWIR", "SwirDev");

clanRepository.save(clan);

Optional<Clan> loaded =
        clanRepository.findById("SWIR");
```
UUID ID
```java
@Id
private UUID id;
```
Set it yourself:
```java
User user = new User(
        UUID.randomUUID(),
        "Alice",
        42
);

userRepository.save(user);
```
Numeric ID
```java
@Id
private Long id;
```
Then:
```java
entity.setId(123L);

repository.save(entity);

repository.findById(123L);
```
MongoDB ObjectId
```java
import org.bson.types.ObjectId;

@Entity("items")
public class Item {

    @Id
    private ObjectId id;

    private String name;

    public Item() {
    }
}
```
The ObjectId is assigned by your application before saving:
```java
item.setId(new ObjectId());

itemsRepository.save(item);
```
If an entity does not contain an `@Id`, contains more than one `@Id`, or has a `null` ID when saving, the operation is rejected.
---
Custom Gson
You can provide your own Gson configuration:
```java
Gson gson = new GsonBuilder()
        .create();

Database database =
        new Database(backend, gson);
```
The configured Gson instance is used for entity serialization and deserialization.
---
Switching Backends
Switching the storage backend only changes the backend initialization. The `Repository<T>` API stays the same.
SQLite
```java
StorageBackend backend =
        new SQLiteBackend(Path.of("data.db"));
```
MySQL
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
MongoDB
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
The rest of the application stays the same:
```java
Database database =
        new Database(backend);

Repository<User> userRepository =
        database.repository(User.class);
```
---
Database Lifecycle
`Database` implements `AutoCloseable`.
Using try-with-resources is recommended:
```java
try (Database database = new Database(
        new SQLiteBackend(Path.of("data.db"))
)) {
    Repository<User> users =
            database.repository(User.class);

    users.save(new User(
            UUID.randomUUID(),
            "Alice",
            42
    ));
}
```
Do not use the `Database` after it has been closed.
---
Backend Notes
SQLite
SQLite is an embedded database and stores data in a local file.
```java
new SQLiteBackend(Path.of("data.db"));
```
MySQL
MySQL uses JDBC with HikariCP for connection pooling.
```java
new MySQLBackend(
        "localhost",
        3306,
        "mydb",
        "user",
        "password"
);
```
MongoDB
MongoDB uses its synchronous Java driver.
```java
new MongoBackend(
        "localhost",
        27017,
        "mydb",
        "user",
        "password"
);
```
---
License
This project is licensed under the MIT License.
