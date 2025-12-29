function countUniqueDays(ranges) {
    const days = new Set();

    ranges.forEach(({ from, to }) => {
        let d = new Date(from);
        d.setHours(0, 0, 0, 0);

        const end = new Date(to);
        end.setHours(0, 0, 0, 0);

        while (d < end) {
            days.add(d.toISOString().slice(0, 10));
            d.setDate(d.getDate() + 1);
        }
    });

    return days.size;
}

function countUniqueDeployments(deployments) {
    if (!deployments.length) return 0;

    // Normalize to calendar days
    const ranges = deployments
        .map(d => ({
            start: new Date(d.from).setHours(0, 0, 0, 0),
            end:   new Date(d.to).setHours(0, 0, 0, 0)
        }))
        .sort((a, b) => a.start - b.start);

    let count = 1;
    let currentEnd = ranges[0].end;

    for (let i = 1; i < ranges.length; i++) {
        const { start, end } = ranges[i];

        // Gap of at least 1 day → new deployment
        if (start > currentEnd + 24 * 60 * 60 * 1000) {
            count++;
            currentEnd = end;
        } else {
            // Merge overlapping or touching ranges
            currentEnd = Math.max(currentEnd, end);
        }
    }

    return count;
}



document.addEventListener("DOMContentLoaded", async () => {
    const params = new URLSearchParams(window.location.search);
    const id = params.get("id");

    const res = await fetch(`/api/${id}`);
    const job = await res.json();

    // Get server deployments
    const serverDeployments = {};

    job.serverList.forEach(server => {
        serverDeployments[server.id] = [];
    });

    job.deployments
        .filter(d => d.active)
        .forEach(d => {
            serverDeployments[d.server].push({
                from: d.dateFrom,
                to: d.dateTo,
                serviceId: d.service
            });
        });

    // Display Job ID and Status
    document.getElementById("job_id").textContent = id;

    const statusElement = document.getElementById("job_status");
    statusElement.textContent = job.solverStatus;

    // Color code status
    if (job.solverStatus === "SOLVING_ACTIVE") {
        statusElement.className = "px-4 py-2 rounded-full text-sm font-semibold bg-yellow-100 text-yellow-800";
    } else if (job.solverStatus === "SOLVED") {
        statusElement.className = "px-4 py-2 rounded-full text-sm font-semibold bg-green-100 text-green-800";
    } else {
        statusElement.className = "px-4 py-2 rounded-full text-sm font-semibold bg-gray-100 text-gray-800";
    }

    document.getElementById("job_score").textContent = job.score;

    // Display Servers
    const serversContainer = document.getElementById("servers_container");

    job.serverList.forEach(server => {
        const serverCard = document.createElement("div");
        serverCard.className = "border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow";
        serverCard.id = `s-${server.id}`;

        const deployments = serverDeployments[server.id] || [];

        const activeDays = countUniqueDays(deployments);
        const uniqueDeployments = countUniqueDeployments(deployments)

        const dailyCost = server.cost.daily ?? 0;
        const totalCost = activeDays * dailyCost;

        const costMessage = activeDays > 0
          ? `<b>Total cost: </b> ${totalCost} + ${(server.cost.allocation + server.cost.deallocation)} * ${uniqueDeployments}`
          : "";

        const deploymentList = deployments.map(d => {
            const serviceName = job.serviceList.find(s => s.id === d.serviceId)?.name;
            return `
                <li class="text-sm text-gray-600">
                    ${serviceName}: ${new Date(d.from).toLocaleDateString()} → ${new Date(d.to).toLocaleDateString()}
                </li>
            `;
        }).join("");

        serverCard.innerHTML = `
            <h3 class="font-bold text-lg text-blue-600 mb-2">${server.name}</h3>

            <p class="text-sm"><b>CPU:</b> ${server.cpuCores}</p>
            <p class="text-sm"><b>RAM:</b> ${server.ramGB} GB</p>

            <div class="border-t my-2"></div>

            <p class="text-sm text-gray-700"><b>Allocation:</b> ${server.cost.allocation}</p>
            <p class="text-sm text-gray-700"><b>Deallocation:</b> ${server.cost.deallocation}</p>
            <p class="text-sm text-gray-700"><b>Active days:</b> ${activeDays}</p>
            <p class="text-sm text-gray-700"><b>Daily cost:</b> ${dailyCost}</p>
            <p class="text-sm font-semibold text-blue-700">${costMessage}</p>

            <div class="border-t my-2"></div>

            <p class="text-sm font-semibold">Deployments:</p>
            <ul class="list-disc ml-4">
                ${deploymentList || "<li class='text-sm text-gray-500'>None</li>"}
            </ul>
        `;

        serversContainer.appendChild(serverCard);
    });

    // Display Services
    const servicesContainer = document.getElementById("services_container");
    job.serviceList.forEach(service => {
        const serviceCard = document.createElement("div");
        serviceCard.className = "border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow";
        serviceCard.innerHTML = `
            <h3 class="font-bold text-lg text-green-600 mb-2">${service.name}</h3>
            <p class="text-sm text-gray-600"><span class="font-semibold">ID:</span> ${service.id}</p>
            <p class="text-sm text-gray-600"><span class="font-semibold">CPU/Instance:</span> ${service.cpuPerInstance}</p>
            <p class="text-sm text-gray-600"><span class="font-semibold">RAM/Instance:</span> ${service.ramPerInstance}</p>
            <p class="text-sm text-gray-600"><span class="font-semibold">Storage/Instance:</span> ${service.storagePerInstance}</p>
            <p class="text-sm text-gray-600"><span class="font-semibold">Max Requests:</span> ${service.maxRequestsPerInstance}</p>
        `;
        servicesContainer.appendChild(serviceCard);
    });

    // Display Requests
    const requestsContainer = document.getElementById("requests_container");
    job.requests.forEach(request => {
        const requestCard = document.createElement("div");
        requestCard.className = "border border-gray-200 rounded-lg p-4 hover:shadow-md transition-shadow";
        requestCard.id = `r-${request.id}`;
        requestCard.innerHTML = `
            <div class="flex justify-between items-start">
                <div>
                    <h3 class="font-bold text-gray-800">Request #${request.id}</h3>
                    <p class="text-sm text-gray-600"><span class="font-semibold">Service:</span> ${request.serviceName}</p>
                    <p class="text-sm text-gray-600"><span class="font-semibold">Query Count:</span> ${request.estimatedQueryCount}</p>
                    <p class="text-sm text-gray-600"><span class="font-semibold">Max Latency SLA:</span> ${request.maxLatencySLA}</p>
                    <p class="text-sm text-gray-600"><span class="font-semibold">Source Region:</span> ${request.sourceRegion || 'N/A'}</p>
                    <p class="text-sm text-gray-600"><span class="font-semibold">Date:</span> ${request.date || 'N/A'}</p>
                </div>
            </div>
        `;
        requestsContainer.appendChild(requestCard);
    });

    // Display Deployments
    const deploymentsContainer = document.getElementById("deployments_container");
    job.deployments.filter(d => d.active).forEach(deployment => {
        const deploymentCard = document.createElement("div");
        const activeClass = deployment.active ? "border-green-400 bg-green-50" : "border-gray-200";
        deploymentCard.className = `border-2 ${activeClass} rounded-lg p-4 hover:shadow-md transition-shadow`;

        const serviceName = deployment.service ? job.serviceList.find(s => s.id === deployment.service)?.name : 'N/A';
        const serverName = deployment.server ? job.serverList.find(s => s.id === deployment.server)?.name : 'N/A';

        deploymentCard.innerHTML = `
            <div class="flex justify-between items-start mb-2">
                <h3 class="font-bold text-gray-800">Deployment #${deployment.id}</h3>
                ${deployment.active ? '<span class="px-2 py-1 bg-green-500 text-white text-xs rounded-full">Active</span>' : '<span class="px-2 py-1 bg-gray-400 text-white text-xs rounded-full">Inactive</span>'}
            </div>
            <div class="grid grid-cols-2 gap-2 text-sm">
                <p class="text-gray-600"><span class="font-semibold">Service:</span> ${serviceName}</p>
                <p class="text-gray-600"><span class="font-semibold">Server:</span> ${serverName}</p>
                <p class="text-gray-600"><span class="font-semibold">From:</span> ${new Date(deployment.dateFrom).toLocaleDateString()}</p>
                <p class="text-gray-600"><span class="font-semibold">To:</span> ${new Date(deployment.dateTo).toLocaleDateString()}</p>
                <p class="text-gray-600"><span class="font-semibold">Requests:</span> ${deployment.requestCount}</p>
                <p class="text-gray-600"><span class="font-semibold">Request IDs:</span> ${deployment.requests.length > 0 ? deployment.requests.join(', ') : 'None'}</p>
            </div>
        `;
        deploymentsContainer.appendChild(deploymentCard);
    });

    // Display Available Dates
    const datesContainer = document.getElementById("dates_container");
    job.availableDates.forEach(date => {
        const dateTag = document.createElement("span");
        dateTag.className = "px-3 py-1 bg-blue-100 text-blue-800 rounded-full text-sm";
        dateTag.textContent = new Date(date).toLocaleString();
        datesContainer.appendChild(dateTag);
    });
});