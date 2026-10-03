package pl.persistence.backend.jdbc;

public record Table(String name, String upsert, String scan, String selectData, String existsById, String deleteById) {
    public static Table create(String name, String upsert) {
        return new Table(name, upsert, "SELECT `id`, `data` FROM " + name, "SELECT `id`, `data` FROM " + name + " WHERE `id` = ?", "SELECT 1 FROM " + name + " WHERE `id` = ?", "DELETE FROM " + name + " WHERE `id` = ?");
    }

    public String deleteIn(int count) {
        return "DELETE FROM " + this.name + " WHERE `id` IN (?" + ",?".repeat(count - 1) + ')';
    }
}
