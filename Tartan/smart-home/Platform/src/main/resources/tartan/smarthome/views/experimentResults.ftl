<#-- @ftlvariable name="" type="tartan.smarthome.views.ExperimentResultsView" -->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>AB Experiment Results – Tartan Smart Home</title>
    <script src="https://cdn.jsdelivr.net/npm/chart.js"></script>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        table { border-collapse: collapse; margin: 20px 0; }
        th, td { border: 1px solid #ccc; padding: 8px 12px; text-align: left; }
        th { background: #f0f0f0; }
        .chart-container { max-width: 600px; margin: 20px 0; }
        h1 { color: #333; }
        h2 { color: #555; margin-top: 28px; }
        .conclusion { background: #f9f9f9; padding: 12px; border-left: 4px solid #4CAF50; margin: 20px 0; }
    </style>
</head>
<body>
    <h1>AB Experiment Results</h1>
    <p>Which report variant was sent to which customer and how it affected light usage.</p>
    <p><strong>Config file:</strong> <code>${configFilePath}</code></p>

    <h2>Per-house summary</h2>
    <table>
        <thead>
            <tr>
                <th>House</th>
                <th>Variant</th>
                <th>Config File</th>
                <th>Snapshots</th>
                <th>Total light usage (ms)</th>
                <th>Avg light usage (ms)</th>
            </tr>
        </thead>
        <tbody>
            <#list houseRows as row>
            <tr>
                <td>${row.houseName}</td>
                <td>${row.variant}</td>
                <td><code>${configFilePath}</code></td>
                <td>${row.snapshotCount}</td>
                <td>${row.totalLightsOnMs}</td>
                <td>${row.avgLightsOnMs?string["0.00"]}</td>
            </tr>
            </#list>
        </tbody>
    </table>

    <h2>Comparison by variant</h2>
    <div class="chart-container">
        <canvas id="variantChart" width="400" height="200"></canvas>
    </div>

    <h2>Conclusion</h2>
    <div class="conclusion">${conclusion}</div>

    <script>
        (function() {
            const ctx = document.getElementById('variantChart').getContext('2d');
            new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: ['Usage only (Variant A)', 'Cost estimate (Variant B)'],
                    datasets: [{
                        label: 'Avg total light usage (ms)',
                        data: [${usageOnlyAvgMs?string["0.00"]}, ${costEstimateAvgMs?string["0.00"]}],
                        backgroundColor: ['rgba(54, 162, 235, 0.6)', 'rgba(255, 99, 132, 0.6)'],
                        borderColor: ['rgb(54, 162, 235)', 'rgb(255, 99, 132)'],
                        borderWidth: 1
                    }]
                },
                options: {
                    responsive: true,
                    scales: {
                        y: { beginAtZero: true }
                    }
                }
            });
        })();
    </script>
</body>
</html>
