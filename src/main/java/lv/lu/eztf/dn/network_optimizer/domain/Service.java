package lv.lu.eztf.dn.network_optimizer.domain;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter @Getter @AllArgsConstructor @NoArgsConstructor
@JsonIdentityInfo(scope = Region.class, property = "id", generator = ObjectIdGenerators.PropertyGenerator.class)
public class Service {
    int id;
    String name;
    float cpuPerInstance;
    float ramPerInstance;
    float storagePerInstance;
    int maxRequestsPerInstance;
}
