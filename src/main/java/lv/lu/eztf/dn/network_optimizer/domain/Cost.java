package lv.lu.eztf.dn.network_optimizer.domain;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter @Getter @AllArgsConstructor @NoArgsConstructor
@JsonIdentityInfo(scope = Cost.class, property = "id", generator = ObjectIdGenerators.PropertyGenerator.class)
public class Cost {
    int id;
    BigDecimal daily;
    BigDecimal allocation;
    BigDecimal deallocation;
}
