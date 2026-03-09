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
import java.util.Date;
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
    /** Weekly report period (ms); same as TartanHomeService so baseline/treatment align with report. */
    private static final long REPORT_PERIOD_MS = 30_000L;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/experiment/results")
    @Timed
    @UnitOfWork
    public ExperimentResultsView experimentResults() {
        long now = System.currentTimeMillis();
        Date baselineFrom = new Date(now - 2 * REPORT_PERIOD_MS);
        Date baselineTo = new Date(now - REPORT_PERIOD_MS);
        Date treatmentFrom = new Date(now - REPORT_PERIOD_MS);
        Date treatmentTo = new Date(now);

        List<TartanHomeData> baselineSnapshots = homeDAO.findAllBetween(baselineFrom, baselineTo);
        List<TartanHomeData> treatmentSnapshots = homeDAO.findAllBetween(treatmentFrom, treatmentTo);

        Map<String, List<TartanHomeData>> baselineByHome = baselineSnapshots.stream()
                .collect(Collectors.groupingBy(TartanHomeData::getHomeName));
        Map<String, List<TartanHomeData>> treatmentByHome = treatmentSnapshots.stream()
                .collect(Collectors.groupingBy(TartanHomeData::getHomeName));

        List<ExperimentResultsView.HouseExperimentRow> rows = new ArrayList<>();
        List<Double> usageOnlyTreatmentTotals = new ArrayList<>();
        List<Double> costEstimateTreatmentTotals = new ArrayList<>();

        for (Map.Entry<String, List<TartanHomeData>> e : treatmentByHome.entrySet()) {
            String houseName = e.getKey();
            List<TartanHomeData> treatment = e.getValue();
            if (treatment.isEmpty()) continue;

            String variant = treatment.stream()
                    .map(TartanHomeData::getReportVariant)
                    .filter(v -> v != null && !v.isEmpty())
                    .findFirst()
                    .orElse("usage_only");

            long baselineMs = baselineByHome.getOrDefault(houseName, List.of()).stream()
                    .mapToLong(s -> s.getMinutesLightsOn() != null ? s.getMinutesLightsOn() : 0L)
                    .max()
                    .orElse(0L);
            int count = treatment.size();
            long treatmentMs = treatment.stream()
                    .mapToLong(s -> s.getMinutesLightsOn() != null ? s.getMinutesLightsOn() : 0L)
                    .max()
                    .orElse(0L);
            double avgMs = treatment.stream()
                    .mapToLong(s -> s.getMinutesLightsOn() != null ? s.getMinutesLightsOn() : 0L)
                    .average()
                    .orElse(0.0);

            rows.add(new ExperimentResultsView.HouseExperimentRow(houseName, variant, count, baselineMs, treatmentMs, avgMs));

            if ("cost_estimate".equals(variant)) {
                costEstimateTreatmentTotals.add((double) treatmentMs);
            } else {
                usageOnlyTreatmentTotals.add((double) treatmentMs);
            }
        }

        double usageOnlyAvgMs = usageOnlyTreatmentTotals.isEmpty() ? 0.0 : usageOnlyTreatmentTotals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double costEstimateAvgMs = costEstimateTreatmentTotals.isEmpty() ? 0.0 : costEstimateTreatmentTotals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        String conclusion;
        if (usageOnlyTreatmentTotals.isEmpty() || costEstimateTreatmentTotals.isEmpty()) {
            conclusion = "Need data from both variants in the treatment period. Run the system for at least 60 seconds (baseline 30s + treatment 30s) so both periods have snapshots.";
        } else if (costEstimateAvgMs < usageOnlyAvgMs) {
            conclusion = "In the treatment period, houses shown the cost estimate (Variant B) had lower average light usage than houses shown usage only (Variant A). Showing cost may encourage reduced consumption.";
        } else if (costEstimateAvgMs > usageOnlyAvgMs) {
            conclusion = "In the treatment period, houses shown usage only (Variant A) had lower average light usage. In this run, the cost estimate variant did not reduce consumption.";
        } else {
            conclusion = "Both variants showed similar average light usage in the treatment period.";
        }

        String configFilePath = "Platform/config.docker.yml (Docker) / Platform/config.yml (local)";
        return new ExperimentResultsView(rows, usageOnlyAvgMs, costEstimateAvgMs, conclusion, configFilePath);
    }
}

