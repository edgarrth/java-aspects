package pe.axiz.payment.infrastructure.rest;
import org.springframework.web.bind.annotation.*; import pe.axiz.payment.infrastructure.aop.DomainWeavingAspect; import java.util.Map;
@RestController @RequestMapping("/api/v1/aop") public class AopDiagnosticsController {
 @GetMapping("/diagnostics") public Map<String,Long> diagnostics(){return Map.of("domainConstructorsWoven",DomainWeavingAspect.constructorJoinPoints(),"privateDomainMethodsWoven",DomainWeavingAspect.privateMethodJoinPoints());}
}