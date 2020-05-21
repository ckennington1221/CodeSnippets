package ck.projectclock;

import java.util.ArrayList;

public class Project
{
    int id;
    String name, description;
    boolean completed = false;
    boolean archived = false;
    boolean active = false;
    ArrayList<TimeEvent> timeEvents = new ArrayList<>();
















    public ArrayList<TimeEvent> getTimeEvents()
    {
        return timeEvents;
    }

    public void setTimeEvents(ArrayList<TimeEvent> timeEvents)
    {
        this.timeEvents = timeEvents;
    }

    public int getId()
    {
        return id;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public boolean isCompleted()
    {
        return completed;
    }

    public void setCompleted(boolean completed)
    {
        this.completed = completed;
    }

    public boolean isArchived()
    {
        return archived;
    }

    public void setArchived(boolean archived)
    {
        this.archived = archived;
    }

    public boolean isActive()
    {
        return active;
    }

    public void setActive(boolean active)
    {
        this.active = active;
    }
}
