package lv.lu.eztf.dn.network_optimizer.domain;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

@Setter @Getter @AllArgsConstructor @NoArgsConstructor
@JsonIdentityInfo(scope = Region.class, property = "id", generator = ObjectIdGenerators.PropertyGenerator.class)
public class Service {
    int id;
    String name;
    float cpuPerInstance;
    float ramPerInstance;
    float storagePerInstance;
    int maxRequestsPerInstance;
    List<Service> dependencies;
    public void dependsOn(Service dependency) {
        // Make our life easier by only making dependencies not null when actually depending on something
        if(this.dependencies == null) {
            this.dependencies = new ArrayList<>();
        }
        this.dependencies.add(dependency);
    }
    public int maxRequests() {
        // Basically if no dependencies then simply look at the instance
        int mr = this.maxRequestsPerInstance;
        if(dependencies != null) {
            // Otherwise each instnce might act as a bottleneck so we do min
            for(Service  dependency : dependencies) {
                mr = Math.min(mr, dependency.maxRequests());
            }
        }
        return mr;
    }
    public Set<Service> allDependenciesIncludingSelf() {
        Set<Service> result = new HashSet<>();
        collect(this, result);
        return result;
    }

    private void collect(Service svc, Set<Service> out) {
        if (!out.add(svc)) return;
        if (svc.getDependencies() != null) {
            for (Service d : svc.getDependencies()) {
                collect(d, out);
            }
        }
    }

    // Includes itself and all dependencies(even those of children)
    public List<Service> totalContainedServices() {
        HashSet<Service> visited = new HashSet<>();
        LinkedList<Service> que = new LinkedList<>();
        que.add(this);
        while(!que.isEmpty()) {
            Service top = que.removeFirst();
            visited.add(top);
            List<Service> dependencies = top.getDependencies();
            if(dependencies != null) {
                for(Service child : top.getDependencies()) {
                    que.push(child);
                }
            }
        }
        return visited.stream().toList();
    }


}
