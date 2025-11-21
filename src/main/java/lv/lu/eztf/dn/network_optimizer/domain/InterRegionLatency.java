package lv.lu.eztf.dn.network_optimizer.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class InterRegionLatency {
    float latency;
    Region region1;
    Region region2;
}
