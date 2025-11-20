package lv.lu.eztf.dn.network_optimizer.domain;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter @Getter @AllArgsConstructor @NoArgsConstructor
@JsonIdentityInfo(scope = Region.class, property = "id", generator = ObjectIdGenerators.PropertyGenerator.class)
public class Cost {
    int id;
    float daily;
    float allocation;
    float deallocation;
}
