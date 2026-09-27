package pe.axiz.payment.infrastructure.aop;

import org.aspectj.lang.annotation.*; import java.util.concurrent.atomic.LongAdder;

@Aspect
public class DomainWeavingAspect {
    private static final LongAdder CONSTRUCTORS=new LongAdder(); private static final LongAdder PRIVATE_METHODS=new LongAdder();
    @Before("initialization(pe.axiz.payment.domain.model.Payment.new(..))") public void paymentConstructed(){CONSTRUCTORS.increment();}
    @Before("execution(private * pe.axiz.payment.domain.model.Payment.*(..))") public void privateDomainMethod(){PRIVATE_METHODS.increment();}
    public static long constructorJoinPoints(){return CONSTRUCTORS.sum();} public static long privateMethodJoinPoints(){return PRIVATE_METHODS.sum();}
}