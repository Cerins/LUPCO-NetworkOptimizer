document.addEventListener("DOMContentLoaded", () => {
    const servers = [];
    const services = [];
    const requests = [];
    const availableDates = [];
    const latencies = [];

    let serverId = 1;
    let serviceId = 1;
    let requestId = 1;

    // Add server
    document.getElementById("submit_server").addEventListener("click", (e) => {
        e.preventDefault();

        const name = document.getElementById("server_name").value;
        const cpu = Number(document.getElementById("cpu_capacity").value);
        const memory = Number(document.getElementById("memory_capacity").value);
        const storage = Number(document.getElementById("storage_capacity").value);
        const region = document.getElementById("server_region").value || null;
        const zone = document.getElementById("server_zone").value || null;

        if (!name || !cpu || !memory || !storage) {
            alert("Fill all required server fields");
            return;
        }

        servers.push({
            id: serverId++,
            name: name,
            cpuCores: cpu,
            ramGb: memory,
            storageGb: storage,
            region: region,
            zone: zone
        });

        document.getElementById("server_form").reset();
        updateServerList();
        console.log("Servers:", servers);
    });

    // Add service
    document.getElementById("submit_service").addEventListener("click", (e) => {
        e.preventDefault();

        const name = document.getElementById("service_name").value;
        const cpuReq = Number(document.getElementById("cpu_required").value);
        const memReq = Number(document.getElementById("memory_required").value);
        const storageReq = Number(document.getElementById("storage_required").value);
        const priority = Number(document.getElementById("priority").value);

        if (!name || !cpuReq || !memReq || !storageReq) {
            alert("Fill all required service fields");
            return;
        }

        services.push({
            id: serviceId++,
            name: name,
            cpuRequired: cpuReq,
            ramRequired: memReq,
            storageRequired: storageReq,
            priority: priority
        });

        document.getElementById("service_form").reset();
        updateServiceList();
        console.log("Services:", services);
    });

    // Add request
    document.getElementById("submit_request").addEventListener("click", (e) => {
        e.preventDefault();

        const serviceName = document.getElementById("request_service_name").value;
        const dateCreated = document.getElementById("date_created").value;
        const durationDays = Number(document.getElementById("duration_days").value);
        const budget = Number(document.getElementById("budget").value);

        if (!serviceName || !dateCreated || !durationDays || !budget) {
            alert("Fill all request fields");
            return;
        }

        requests.push({
            id: requestId++,
            serviceName: serviceName,
            dateCreated: new Date(dateCreated).toISOString(),
            durationDays: durationDays,
            budget: budget,
            assignedDeployment: null
        });

        document.getElementById("request_form").reset();
        updateRequestList();
        console.log("Requests:", requests);
    });

    // Add available date
    document.getElementById("submit_date").addEventListener("click", (e) => {
        e.preventDefault();

        const date = document.getElementById("available_date").value;

        if (!date) {
            alert("Select a date");
            return;
        }

        const isoDate = new Date(date).toISOString();
        availableDates.push(isoDate);

        document.getElementById("date_form").reset();
        updateDateList();
        console.log("Available Dates:", availableDates);
    });

    // Add latency
    document.getElementById("submit_latency").addEventListener("click", (e) => {
        e.preventDefault();

        const fromRegion = document.getElementById("from_region").value;
        const toRegion = document.getElementById("to_region").value;
        const latencyMs = Number(document.getElementById("latency_ms").value);

        if (!fromRegion || !toRegion || !latencyMs) {
            alert("Fill all latency fields");
            return;
        }

        latencies.push({
            fromRegion: fromRegion,
            toRegion: toRegion,
            latencyMs: latencyMs
        });

        document.getElementById("latency_form").reset();
        updateLatencyList();
        console.log("Latencies:", latencies);
    });

    // Update display lists
    function updateServerList() {
        const list = document.getElementById("server_list");
        if (!list) return;
        list.innerHTML = '<h3 class="font-bold mb-2">Added Servers:</h3>' +
            servers.map((s, i) => `
                <div class="border p-2 mb-2 rounded bg-gray-50 flex justify-between items-start">
                    <div>
                        <strong>${s.name}</strong> - CPU: ${s.cpuCores}, RAM: ${s.ramGb}GB, Storage: ${s.storageGb}GB
                        ${s.region ? `<br>Region: ${s.region}${s.zone ? `, Zone: ${s.zone}` : ''}` : ''}
                    </div>
                    <button onclick="removeServer(${i})" class="text-red-500 hover:text-red-700">✕</button>
                </div>
            `).join('');
    }

    function updateServiceList() {
        const list = document.getElementById("service_list");
        if (!list) return;
        list.innerHTML = '<h3 class="font-bold mb-2">Added Services:</h3>' +
            services.map((s, i) => `
                <div class="border p-2 mb-2 rounded bg-gray-50 flex justify-between items-start">
                    <div>
                        <strong>${s.name}</strong> - CPU: ${s.cpuRequired}, RAM: ${s.ramRequired}GB, Storage: ${s.storageRequired}GB, Priority: ${s.priority}
                    </div>
                    <button onclick="removeService(${i})" class="text-red-500 hover:text-red-700">✕</button>
                </div>
            `).join('');
    }

    function updateRequestList() {
        const list = document.getElementById("request_list");
        if (!list) return;
        list.innerHTML = '<h3 class="font-bold mb-2">Added Requests:</h3>' +
            requests.map((r, i) => `
                <div class="border p-2 mb-2 rounded bg-gray-50 flex justify-between items-start">
                    <div>
                        <strong>${r.serviceName}</strong> - ${r.durationDays} days, Budget: $${r.budget}
                        <br><small>${new Date(r.dateCreated).toLocaleString()}</small>
                    </div>
                    <button onclick="removeRequest(${i})" class="text-red-500 hover:text-red-700">✕</button>
                </div>
            `).join('');
    }

    function updateDateList() {
        const list = document.getElementById("date_list");
        if (!list) return;
        list.innerHTML = '<h3 class="font-bold mb-2">Available Dates:</h3>' +
            availableDates.map((d, i) => `
                <div class="border p-2 mb-2 rounded bg-gray-50 flex justify-between items-center">
                    <span>${new Date(d).toLocaleString()}</span>
                    <button onclick="removeDate(${i})" class="text-red-500 hover:text-red-700">✕</button>
                </div>
            `).join('');
    }

    function updateLatencyList() {
        const list = document.getElementById("latency_list");
        if (!list) return;
        list.innerHTML = '<h3 class="font-bold mb-2">Latencies:</h3>' +
            latencies.map((l, i) => `
                <div class="border p-2 mb-2 rounded bg-gray-50 flex justify-between items-center">
                    <span>${l.fromRegion} → ${l.toRegion}: ${l.latencyMs}ms</span>
                    <button onclick="removeLatency(${i})" class="text-red-500 hover:text-red-700">✕</button>
                </div>
            `).join('');
    }

    // Remove functions (make them global)
    window.removeServer = (i) => { servers.splice(i, 1); updateServerList(); };
    window.removeService = (i) => { services.splice(i, 1); updateServiceList(); };
    window.removeRequest = (i) => { requests.splice(i, 1); updateRequestList(); };
    window.removeDate = (i) => { availableDates.splice(i, 1); updateDateList(); };
    window.removeLatency = (i) => { latencies.splice(i, 1); updateLatencyList(); };

    // Submit final JSON
    document.getElementById("submit_button").addEventListener("click", async () => {
        // Generate deployments based on number of requests
        const numDeployments = Math.max(requests.length, 8);
        const deployments = Array.from({length: numDeployments}, (_, i) => ({
            id: i + 1,
            service: null,
            server: null,
            dateFrom: null,
            dateTo: null,
            requests: []
        }));

        const payload = {
            serverList: servers,
            serviceList: services,
            requests: requests,
            availableDates: availableDates,
            deployments: deployments,
            latencies: latencies
        };

        console.log("FINAL JSON:", JSON.stringify(payload, null, 2));

        try {
            const res = await fetch("/api", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload, null, 2)
            });

            if (!res.ok) {
                alert("API error: " + res.status);
                return;
            }

            console.log("API Response:", res);

            alert("Job submitted successfully!");

            const id = await res.text();

            window.location.href = `/job.html?id=${id}`;
        } catch (err) {
            console.error("Request failed:", err);
            alert("Network error: " + err.message);
        }
    });

    // Export JSON button
    const exportBtn = document.getElementById("export_json_btn");
    if (exportBtn) {
        exportBtn.addEventListener("click", () => {
            const numDeployments = Math.max(requests.length, 8);
            const deployments = Array.from({length: numDeployments}, (_, i) => ({
                id: i + 1,
                service: null,
                server: null,
                dateFrom: null,
                dateTo: null,
                requests: []
            }));

            const payload = {
                servers: servers,
                services: services,
                requests: requests,
                availableDates: availableDates,
                deployments: deployments,
                latencies: latencies
            };

            const jsonStr = JSON.stringify(payload, null, 2);
            const blob = new Blob([jsonStr], {type: 'application/json'});
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'network-optimizer-config.json';
            a.click();
            URL.revokeObjectURL(url);
        });
    }

    // JSON template button
    const templateBtn = document.getElementById("json_template_btn");
    if (templateBtn) {
        templateBtn.addEventListener("click", () => {
            const template = {
                servers: [
                    {
                        id: 1,
                        name: "s1",
                        cpuCores: 8,
                        ramGb: 32.0,
                        storageGb: 500.0,
                        region: null,
                        zone: null
                    }
                ],
                services: [
                    {
                        id: 1,
                        name: "a1",
                        cpuRequired: 2.0,
                        ramRequired: 4.0,
                        storageRequired: 10.0,
                        priority: 0
                    }
                ],
                requests: [
                    {
                        id: 1,
                        serviceName: "a1",
                        dateCreated: "2024-12-01T10:00:00Z",
                        durationDays: 10,
                        budget: 50.0,
                        assignedDeployment: null
                    }
                ],
                availableDates: [
                    "2024-12-01T00:00:00Z",
                    "2024-12-04T00:00:00Z"
                ],
                deployments: [
                    {
                        id: 1,
                        service: null,
                        server: null,
                        dateFrom: null,
                        dateTo: null,
                        requests: []
                    }
                ],
                latencies: [
                    {
                        fromRegion: "eu-west",
                        toRegion: "us-east",
                        latencyMs: 85.0
                    }
                ]
            };

            const jsonStr = JSON.stringify(template, null, 2);
            const blob = new Blob([jsonStr], {type: 'application/json'});
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = 'network-optimizer-template.json';
            a.click();
            URL.revokeObjectURL(url);
        });
    }

    // JSON import handling
    const importInput = document.getElementById("json_import");
    if (importInput) {
        importInput.addEventListener("change", (e) => {
            const file = e.target.files[0];
            if (!file) return;

            const reader = new FileReader();
            reader.onload = (event) => {
                try {
                    const imported = JSON.parse(event.target.result);

                    // Clear existing data
                    servers.length = 0;
                    services.length = 0;
                    requests.length = 0;
                    availableDates.length = 0;
                    latencies.length = 0;

                    // Import data
                    if (imported.serverList) servers.push(...imported.serverList);
                    if (imported.serviceList) services.push(...imported.serviceList);
                    if (imported.requests) requests.push(...imported.requests);
                    if (imported.availableDates) availableDates.push(...imported.availableDates);
                    if (imported.latencies) latencies.push(...imported.latencies);

                    // Update ID counters
                    serverId = Math.max(...servers.map(s => s.id), 0) + 1;
                    serviceId = Math.max(...services.map(s => s.id), 0) + 1;
                    requestId = Math.max(...requests.map(r => r.id), 0) + 1;

                    // Update all lists
                    updateServerList();
                    updateServiceList();
                    updateRequestList();
                    updateDateList();
                    updateLatencyList();

                    alert('JSON imported successfully!');
                } catch (err) {
                    alert('Error parsing JSON: ' + err.message);
                }
            };
            reader.readAsText(file);
        });
    }
});