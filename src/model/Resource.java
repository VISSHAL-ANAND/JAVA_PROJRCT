package model;

public class Resource {
    private final int id;
    private String name;
    private String type;
    private int quantity;
    private boolean available;

    public Resource(int id, String name, String type, int quantity, boolean available) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.quantity = quantity;
        this.available = available;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public int getQuantity() { return quantity; }
    public boolean isAvailable() { return available; }

    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setAvailable(boolean available) { this.available = available; }
}
