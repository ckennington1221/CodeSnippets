package ck.projectclock;

public class TimeEvent
{
    int projectID;
    long timeIn;
    long timeOut;

    public TimeEvent(int id, long in, long out)
    {
        this.projectID = id;
        this.timeIn = in;
        this. timeOut = out;
    }

    public int getProjectID()
    {
        return projectID;
    }

    public void setProjectID(int projectID)
    {
        this.projectID = projectID;
    }

    public long getTimeIn()
    {
        return timeIn;
    }

    public void setTimeIn(long timeIn)
    {
        this.timeIn = timeIn;
    }

    public long getTimeOut()
    {
        return timeOut;
    }

    public void setTimeOut(long timeOut)
    {
        this.timeOut = timeOut;
    }
}
