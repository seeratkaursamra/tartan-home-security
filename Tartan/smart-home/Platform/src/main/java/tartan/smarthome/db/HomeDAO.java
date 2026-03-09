package tartan.smarthome.db;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.Session;
import org.hibernate.SessionException;
import org.hibernate.SessionFactory;
import tartan.smarthome.core.TartanHomeData;

import java.util.Date;
import java.util.List;

/**
 * The data access object to log the house data
 */
public class HomeDAO extends AbstractDAO<TartanHomeData> {
    // Keep a reference to the session
    private SessionFactory factory = null;

    public HomeDAO(SessionFactory factory) {
        super(factory);
        this.factory = factory;
    }

    /**
     * Save the taratn home data to the database
     * @param tartanHomeData the data to save
     */
    public void create(TartanHomeData tartanHomeData) {
        try {
            // There is a dropwizard way to establish a Hibernate session outside of Jersey
            // but this is more reliable
            Session session = factory.openSession();
            session.beginTransaction();
            session.save(tartanHomeData);
            session.getTransaction().commit();
            session.close();
        } catch (SessionException sx) {/* Nothing to do */ }
    }

    /**
     * Find all historic home snapshots (for experiment analysis).
     * Must be called within a UnitOfWork / transaction context.
     * @return all TartanHomeData records
     */
    public List<TartanHomeData> findAll() {
        return currentSession().createQuery("from TartanHomeData", TartanHomeData.class).list();
    }

    /**
     * Find snapshots within a time range (for baseline and treatment period analysis).
     * Must be called within a UnitOfWork / transaction context.
     * @param fromInclusive start of range (inclusive)
     * @param toInclusive end of range (inclusive)
     * @return snapshots with createTimeStamp in [from, to]
     */
    public List<TartanHomeData> findAllBetween(Date fromInclusive, Date toInclusive) {
        return currentSession()
                .createQuery("from TartanHomeData t where t.createTimeStamp >= :fromTime and t.createTimeStamp <= :toTime", TartanHomeData.class)
                .setParameter("fromTime", fromInclusive)
                .setParameter("toTime", toInclusive)
                .list();
    }
}
