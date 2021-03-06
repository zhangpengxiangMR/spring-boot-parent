package com.pykj.v2.spring.framework.context;

import com.pykj.v2.spring.framework.annotation.PYAutowired;
import com.pykj.v2.spring.framework.annotation.PYController;
import com.pykj.v2.spring.framework.annotation.PYService;
import com.pykj.v2.spring.framework.aop.PYJdkDynamicAopProxy;
import com.pykj.v2.spring.framework.aop.config.PYAopConfig;
import com.pykj.v2.spring.framework.aop.support.PYAdvisedSupport;
import com.pykj.v2.spring.framework.beans.PYBeanWrapper;
import com.pykj.v2.spring.framework.beans.config.PYBeanDefinition;
import com.pykj.v2.spring.framework.beans.support.PYBeanDefinitionReader;
import com.pykj.v2.spring.framework.beans.support.PYDefaultListableBeanFactory;
import com.pykj.v2.spring.framework.core.PYBeanFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 职责：完成Bean的创建和DI
 */
public class PYApplicationContext implements PYBeanFactory {

    //private String [] configLocations;
    /**
     * beanDefinition缓存
     */
    //private Map<String, PYBeanDefinition> beanDefinitionMap = new HashMap<>();

    private PYDefaultListableBeanFactory regitry = new PYDefaultListableBeanFactory();

    private PYBeanDefinitionReader reader;

    /**
     * key:beanName，value:BeanWrapper
     */
    private Map<String, PYBeanWrapper> factoryBeanInstanceCache = new HashMap<>();

    /**
     * key:Beanname,value:存储对象实例化对象
     */
    private Map<String, Object> factoryBeanObjectCache = new HashMap<>();

    public PYApplicationContext(String... configLocations) {
        //this.configLocations = configLocations;
        //1、加载配置文件
        reader = new PYBeanDefinitionReader(configLocations);
        //2、解析配置文件，封装成BeanDefinition
        List<PYBeanDefinition> beanDefiinitions = reader.loadBeanDefinitions();
        //3、把BeanDefinition缓存起来
        try {
            regitry.doRegistBeanDefinition(beanDefiinitions);
        } catch (Exception e) {
            e.printStackTrace();
        }
        doAutowrited();
    }

    private void doAutowrited() {
        //bean并没有真正的实例化，还只是配置阶段
        for (Map.Entry<String, PYBeanDefinition> stringPYBeanDefinitionEntry : this.regitry.beanDefinitionMap.entrySet()) {
            String beanName = stringPYBeanDefinitionEntry.getKey();
            getBean(beanName);
        }
    }


    /**
     * 调用GetBean进行Bean的初始化阶段
     *
     * @param beanName
     * @return
     */
    @Override
    public Object getBean(String beanName) {
        //1、获取BeanDefinition配置
        PYBeanDefinition pyBeanDefinition = this.regitry.beanDefinitionMap.get(beanName);
        //2、反射实例化newInstance()
        Object instance = instantiateBean(beanName, pyBeanDefinition);
        //3、封装成一个BeanWrapper
        PYBeanWrapper pyBeanWrapper = new PYBeanWrapper(instance);
        //4、保存到IoC容器
        factoryBeanInstanceCache.put(beanName, pyBeanWrapper);
        //5、执行依赖注入
        populateBean(beanName, pyBeanDefinition, pyBeanWrapper);
        return pyBeanWrapper.getWrapperInstance();
    }

    private void populateBean(String beanName, PYBeanDefinition pyBeanDefinition, PYBeanWrapper beanWrapper) {
        //可能涉及到循环依赖？
        //A{ B b}
        //B{ A b}
        //用两个缓存，循环两次
        //1、把第一次读取结果为空的BeanDefinition存到第一个缓存
        //2、等第一次循环之后，第二次循环再检查第一次的缓存，再进行赋值

        Object instance = beanWrapper.getWrapperInstance();

        Class<?> clazz = beanWrapper.getWrapperClass();

        //在Spring中@Component
        if (!(clazz.isAnnotationPresent(PYController.class) || clazz.isAnnotationPresent(PYService.class))) {
            return;
        }

        //把所有的包括private/protected/default/public 修饰字段都取出来
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(PYAutowired.class)) {
                continue;
            }

            PYAutowired autowired = field.getAnnotation(PYAutowired.class);

            //如果用户没有自定义的beanName，就默认根据类型注入
            String autowiredBeanName = autowired.value().trim();
            if ("".equals(autowiredBeanName)) {
                //field.getType().getName() 获取字段的类型
                autowiredBeanName = field.getType().getName();
            }
            //暴力访问
            field.setAccessible(true);
            try {
                if (this.factoryBeanInstanceCache.get(autowiredBeanName) == null) {
                    continue;
                }
                field.set(instance, this.factoryBeanInstanceCache.get(autowiredBeanName).getWrapperInstance());
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                continue;
            }
        }
    }

    /**
     * 创建真正的实例化对象
     *
     * @param beanName
     * @param pyBeanDefinition
     * @return
     */
    private Object instantiateBean(String beanName, PYBeanDefinition pyBeanDefinition) {
        String className = pyBeanDefinition.getBeanClassName();
        Object instance = null;
        try {
            if (this.factoryBeanObjectCache.containsKey(beanName)) {
                instance = this.factoryBeanObjectCache.get(beanName);
            } else {
                Class<?> clazz = Class.forName(className);
                instance = clazz.newInstance();

                //开始AOP，判断收满足条件，如果满足则就直接返回代理对象,否则返回原生对象
                //1、加载AOP配置文件
                PYAdvisedSupport config = instantionAopConfig(pyBeanDefinition);
                config.setTargetClass(clazz);
                config.setTarget(instance);
                if (config.pointCutMath()) {
                    instance = new PYJdkDynamicAopProxy(config).getProxy();
                }
                //结束AOP
                this.factoryBeanObjectCache.put(beanName, instance);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return instance;
    }

    private PYAdvisedSupport instantionAopConfig(PYBeanDefinition pyBeanDefinition) {
        PYAopConfig config = new PYAopConfig();
        //切面表达式expression
        config.setPointCut(this.reader.getConfig().getProperty("pointCut"));
        //切面类
        config.setAspectClass(this.reader.getConfig().getProperty("aspectClass"));
        //前置通知回调方法
        config.setAspectBefore(this.reader.getConfig().getProperty("aspectBefore"));
        //后置通知回调方法
        config.setAspectAfter(this.reader.getConfig().getProperty("aspectAfter"));
        //异常通知回调方法
        config.setAspectAfterThrow(this.reader.getConfig().getProperty("aspectAfterThrow"));
        //异常类型捕获
        config.setAspectAfterThrowingName(this.reader.getConfig().getProperty("aspectAfterThrowingName"));
        return new PYAdvisedSupport(config);
    }

    @Override
    public Object getBean(Class beanClass) {
        return getBean(beanClass.getName());
    }

    /**
     * 获取BeanDefinition
     *
     * @return
     */
    public int getBeanDefiitionCount() {
        return this.regitry.beanDefinitionMap.size();
    }

    public String[] getBeanDefinitionNames() {
        return this.regitry.beanDefinitionMap.keySet().toArray(new String[this.regitry.beanDefinitionMap.size()]);
    }

    public Properties getConfig() {
        return this.reader.getConfig();
    }
}
