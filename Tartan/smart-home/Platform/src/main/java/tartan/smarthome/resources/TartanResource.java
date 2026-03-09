package tartan.smarthome.resources;

import com.codahale.metrics.annotation.Timed;
import io.dropwizard.auth.Auth;
import io.dropwizard.hibernate.UnitOfWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tartan.smarthome.TartanHomeSettings;
import tartan.smarthome.auth.TartanUser;
import tartan.smarthome.core.TartanHome;
import tartan.smarthome.core.TartanHomeData;
import tartan.smarthome.db.HomeDAO;
import tartan.smarthome.views.ExperimentResultsView;
import tartan.smarthome.views.SmartHomeView;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The resource class implements the HTTP handlers via Jersey.
 *  @see <a href="https://www.dropwizard.io/1.0.0/docs/getting-started.html#creating-a-resource-class">Dropwizard Resouces</a>
 */
@Path("/smarthome")
@Produces(MediaType.APPLICATION_JSON)
public class TartanResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(TartanResource.class);

    // There is one service per home
    private ArrayList<TartanHomeService> services;
    private final HomeDAO homeDAO;

    /**
     * Create and connect to a list of houses
     * @param houses the settings for each hose
     * @param homeDAO the historian
     * @param historyTimer how often to log history
     */
    public TartanResource(List<TartanHomeSettings> houses, HomeDAO homeDAO, Integer historyTimer) {
        this.homeDAO = homeDAO;
        this.services = new ArrayList<>(houses.size());
        for (TartanHomeSettings homeSettings : houses) {
            TartanHomeService service = new TartanHomeService(homeDAO);
            service.initializeSettings(homeSettings, historyTimer);

            if (!service.isConnected()) {
                try {

                    service.connect();
                    this.services.add(service);
                    LOGGER.info("Connected to house " + service.getName() + " @ " + service.getAddress());

                } catch (TartanHomeConnectException thce) {
                    LOGGER.error("Could not connect to house " + service.getName() + " @ " + service.getAddress());
                }

                startHistorian(service);
            }
        }
    }

    /**
     * Start the historian
     * @param service the service to start logging
     */
    public void startHistorian(TartanHomeService service) {
        if (service.isConnected()) {
            service.startHistorian();
        }
    }

    /**
     * Fetch the service for a house
     * @param houseName the target house
     * @return the service or null if not found
     */
    private TartanHomeService getHomeService(String houseName) {
        for (TartanHomeService h : services) {
            if (h.getName().equals(houseName)) {
                return h;
            }
        }
        return null;
    }

    /**
     * Fetch the current house state via HTTP GET. Managed by Jersey
     * @param house the house
     * @param user the user allowed to access this house
     * @return a view of the house or null
     */
    @GET
    @Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
    @Path("/state/{house}")
    @Timed
    @UnitOfWork
    public SmartHomeView state(@PathParam("house") String house,  @Auth TartanUser user) {
        // There are better ways to check authorization, but this works fine
        if (user.getHouse().equals(house)) {
            LOGGER.info("Received a house GET for house: " + house);
            TartanHomeService service = getHomeService(house);
            if (service == null) return null;

            return new SmartHomeView(service.getState());
        }
        return null;
    }

    /**
     * update the house state via a HTTP POST. Managed by Jersey
     * @param house the house
     * @param user the user allowed to access this house
     * @param h the new state
     * @return either HTTP OK or UNAUTHORIZED
     */
    @POST
    @Path("/update/{house}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Timed
    public Response update(@PathParam("house") String house, @Auth TartanUser user, TartanHome h) {
        if (user.getHouse().equals(house)) {
            LOGGER.info("Received a house POST to house " + house);
            TartanHomeService service = getHomeService(house);
            if (service != null) {
                // tell the house about the update
                service.setState(h);

                return Response
                        .status(Response.Status.OK)
                        .build();
            }
        }
        return Response
                .status(Response.Status.UNAUTHORIZED)
                .build();
    }

    /**
     * AB experiment results: which report variant was sent to which customer and how it affected light usage.
     * No auth required (admin/analyst page).
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/experiment/results")
    @Timed
    @UnitOfWork
    public ExperimentResultsView experimentResults() {
        List<TartanHomeData> all = homeDAO.findAll();

        // Group by home name: for each house, variant (first), count, total = max(minutesLightsOn) since cumulative, avg = mean(minutesLightsOn)
        Map<String, List<TartanHomeData>> byHome = all.stream()
                .collect(Collectors.groupingBy(TartanHomeData::getHomeName));

        List<ExperimentResultsView.HouseExperimentRow> rows = new ArrayList<>();
        List<Double> usageOnlyTotals = new ArrayList<>();
        List<Double> costEstimateTotals = new ArrayList<>();

        for (Map.Entry<String, List<TartanHomeData>> e : byHome.entrySet()) {
            String houseName = e.getKey();
            List<TartanHomeData> snapshots = e.getValue();
            if (snapshots.isEmpty()) continue;

            String variant = snapshots.stream()
                    .map(TartanHomeData::getReportVariant)
                    .filter(v -> v != null && !v.isEmpty())
                    .findFirst()
                    .orElse("usage_only");

            int count = snapshots.size();
            long totalMs = snapshots.stream()
                    .mapToLong(s -> s.getMinutesLightsOn() != null ? s.getMinutesLightsOn() : 0L)
                    .max()
                    .orElse(0L);
            double avgMs = snapshots.stream()
                    .mapToLong(s -> s.getMinutesLightsOn() != null ? s.getMinutesLightsOn() : 0L)
                    .average()
                    .orElse(0.0);

            rows.add(new ExperimentResultsView.HouseExperimentRow(houseName, variant, count, totalMs, avgMs));

            if ("cost_estimate".equals(variant)) {
                costEstimateTotals.add((double) totalMs);
            } else {
                usageOnlyTotals.add((double) totalMs);
            }
        }

        double usageOnlyAvgMs = usageOnlyTotals.isEmpty() ? 0.0 : usageOnlyTotals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double costEstimateAvgMs = costEstimateTotals.isEmpty() ? 0.0 : costEstimateTotals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        String conclusion;
        if (usageOnlyTotals.isEmpty() || costEstimateTotals.isEmpty()) {
            conclusion = "Need data from both variants to compare. Run the system and let the historian collect snapshots.";
        } else if (costEstimateAvgMs < usageOnlyAvgMs) {
            conclusion = "Houses shown the cost estimate (Variant B) had lower average light usage than houses shown usage only (Variant A). Showing cost may encourage reduced consumption.";
        } else if (costEstimateAvgMs > usageOnlyAvgMs) {
            conclusion = "Houses shown usage only (Variant A) had lower average light usage. In this run, the cost estimate variant did not reduce consumption.";
        } else {
            conclusion = "Both variants showed similar average light usage in this experiment.";
        }

        String configFilePath = "Platform/config.docker.yml (Docker) / Platform/config.yml (local)";
        return new ExperimentResultsView(rows, usageOnlyAvgMs, costEstimateAvgMs, conclusion, configFilePath);
    }
}

