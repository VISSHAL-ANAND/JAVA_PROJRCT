package model;

public class Issue {
    private final int id;
    private String title, description, category, location, attachmentPath;
    private Priority priority;
    public Issue(int id,String title,String description,String category,Priority priority){this(id,title,description,category,null,null,priority);}
    public Issue(int id,String title,String description,String category,String location,String attachmentPath,Priority priority){
        this.id=id;this.title=title;this.description=description;this.category=category;this.location=location;this.attachmentPath=attachmentPath;this.priority=priority;
    }
    public int getId(){return id;} public String getTitle(){return title;} public String getDescription(){return description;}
    public String getCategory(){return category;} public String getLocation(){return location;} public String getAttachmentPath(){return attachmentPath;} public Priority getPriority(){return priority;}
    public void setTitle(String v){title=v;} public void setDescription(String v){description=v;} public void setCategory(String v){category=v;}
    public void setLocation(String v){location=v;} public void setAttachmentPath(String v){attachmentPath=v;} public void setPriority(Priority v){priority=v;}
}
