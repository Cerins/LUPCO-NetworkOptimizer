package lv.lu.eztf.dn.network_optimizer.domain;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@JsonIdentityInfo(scope = Region.class, property = "id", generator = ObjectIdGenerators.PropertyGenerator.class)
public class Request {
    int id;
    String serviceName;
    Date date;
    int estimatedQueryCount;
    float maxLatencySLA;

    Region sourceRegion;
}
