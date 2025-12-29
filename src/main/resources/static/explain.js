let cachedExplanation = null;
let jobData = null;

async function fetchExplanation(jobId) {
    if (cachedExplanation) {
        injectExplanationIntoCards(cachedExplanation);
        return;
    }

    try {
        const response = await fetch(`/api/explanation/${jobId}`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const data = await response.json();
        cachedExplanation = data;
        injectExplanationIntoCards(data);
    } catch (error) {
        console.error('Error fetching explanation:', error);
        displayExplanationError(error.message);
    }
}

function setJobData(data) {
    jobData = data;
}

function injectExplanationIntoCards(data) {
    // Inject server costs into server cards
    if (data.serverCosts && data.serverCosts.length > 0) {
        data.serverCosts.forEach(serverCost => {
            injectServerCost(serverCost);
        });
    }

    // Inject problematic requests into request cards
    if (data.problematicRequests && data.problematicRequests.length > 0) {
        data.problematicRequests.forEach(problemRequest => {
            injectProblematicRequest(problemRequest);
        });
    }
}

function injectServerCost(serverCost) {
    const serverId = serverCost.serverId;
    const serverCard = document.getElementById(`s-${serverId}`);

    if (!serverCard) {
        console.warn(`Server card not found for ID: s-${serverId}`);
        return;
    }

    // Check if already injected
    if (serverCard.querySelector('.explanation-data')) {
        return;
    }

    const totalCost = serverCost.softCost || 0;
    const costEntries = Object.entries(serverCost.costByConstraint || {});

    const constraintDetails = costEntries.map(([constraint, cost]) =>
        `<div class="text-xs py-1 flex justify-between">
            <span>${constraint}</span>
            <span class="font-semibold">${cost}</span>
        </div>`
    ).join('');

    const costHtml = `
        <div class="explanation-data mt-4 pt-4 border-t border-gray-300">
            <div class="mb-2">
                <div class="flex items-center justify-between mb-2">
                    <span class="text-sm font-bold text-gray-700">Servers cost:</span>
                    <span class="text-lg font-bold px-2 py-1 rounded ${totalCost < 0 ? 'bg-blue-200 text-blue-700' : 'bg-blue-200 text-blue-700'}">
                        ${totalCost}
                    </span>
                </div>
            </div>
            <details class="mt-2">
                <summary class="text-xs font-semibold text-gray-600 cursor-pointer hover:text-gray-800">
                    Cost Breakdown (${costEntries.length} constraints)
                </summary>
                <div class="mt-2 pl-2 border-l-2 border-gray-300 bg-gray-50 p-2 rounded">
                    ${constraintDetails || '<div class="text-xs text-gray-500">No details available</div>'}
                </div>
            </details>
        </div>
    `;

    serverCard.insertAdjacentHTML('beforeend', costHtml);
}

function injectProblematicRequest(problemRequest) {
    const requestId = problemRequest.requestId;
    const requestCard = document.getElementById(`r-${requestId}`);

    if (!requestCard) {
        console.warn(`Request card not found for ID: r-${requestId}`);
        return;
    }

    const hardScore = problemRequest.hardScore || 0;
    const softScore = problemRequest.softScore || 0;
    const totalScore = hardScore + softScore;

    const violations = Object.entries(problemRequest.violations || {}).map(([violationType, violationData]) => {
        const entities = violationData.involvedEntities || [];

        const entitiesHtml = entities.map((entity) => {
            if (entity.serviceName) {
                // Request entity
                return `
                    <div class="text-xs border p-2 rounded mt-1">
                        <div class="font-semibold mb-1"> Request</div>
                        <div><span class="font-medium">Service:</span> ${entity.serviceName}</div>
                        <div><span class="font-medium">Date:</span> ${new Date(entity.date).toLocaleString()}</div>
                        <div><span class="font-medium">Queries:</span> ${entity.estimatedQueryCount.toLocaleString()}</div>
                        <div><span class="font-medium">Max Latency:</span> ${entity.maxLatencySLA}ms</div>
                    </div>
                `;
            } else if (entity.service) {
                // Deployment entity
                return `
                    <div class="text-xs border p-2 rounded mt-1">
                        <div class="font-semibold mb-1"> Deployment</div>
                        <div><span class="font-medium">Service:</span> ${entity.service.name}</div>
                        <div><span class="font-medium">Server:</span> ${entity.server.name}</div>
                        <div><span class="font-medium">Period:</span> ${new Date(entity.dateFrom).toLocaleDateString()} - ${new Date(entity.dateTo).toLocaleDateString()}</div>
                    </div>
                `;
            }
            return '';
        }).join('');

        return `
            <div class="mb-2 last:mb-0 bg-white p-2 rounded border border-gray-200">
                <div class="font-semibold text-xs mb-1 text-gray-800">${violationType}</div>
                ${entitiesHtml}
            </div>
        `;
    }).join('');

    const problemHtml = `
        <div class="explanation-data mt-4 pt-4 border-t-2 ${hardScore < 0 ? 'border-red-400' : 'border-orange-400'}">
            <div class="flex items-center justify-between mb-2">
                <span class="text-sm font-bold text-gray-700">Constraint Violations:</span>
                <div class="text-right">
                    <div class="text-lg font-bold ${hardScore < 0 ? 'text-red-600' : 'text-orange-600'}">${totalScore}</div>
                    <div class="text-xs text-gray-600">H: ${hardScore} | S: ${softScore}</div>
                </div>
            </div>
            <div class="${hardScore < 0 ? 'bg-red-50' : 'bg-orange-50'} p-3 rounded-lg border ${hardScore < 0 ? 'border-red-200' : 'border-orange-200'}">
                <div class="text-xs font-semibold mb-2 ${hardScore < 0 ? 'text-red-800' : 'text-orange-800'}">
                    ${Object.keys(problemRequest.violations || {}).length} violation(s) detected
                </div>
                <details class="mt-2">
                    <summary class="text-xs font-semibold text-gray-700 cursor-pointer hover:text-gray-900">
                        View Details
                    </summary>
                    <div class="mt-2 space-y-2">
                        ${violations || '<div class="text-xs text-gray-500">No details available</div>'}
                    </div>
                </details>
            </div>
        </div>
    `;

    requestCard.insertAdjacentHTML('beforeend', problemHtml);
}

function displayExplanationError(errorMessage) {
    const container = document.getElementById('expanded_score');
    if (!container) return;

    container.innerHTML = `
        <div class="mt-6 bg-red-50 border-2 border-red-300 rounded-lg p-5">
            <div class="flex items-start">
                <svg class="w-6 h-6 text-red-600 mr-3 flex-shrink-0 mt-0.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                </svg>
                <div>
                    <div class="font-bold text-red-800 text-lg mb-1">Error Loading Job</div>
                    <div class="text-sm text-red-700">${errorMessage}</div>
                    <div class="text-xs text-red-600 mt-2">Please submit a new job.</div>
                </div>
            </div>
        </div>
    `;
}

// Initialize explanation
document.addEventListener('DOMContentLoaded', () => {
  const params = new URLSearchParams(window.location.search);
    const id = params.get("id");
    fetchExplanation(id);
});

// Export function to be called from job.js
window.setExplanationJobData = setJobData;