
package com.example.healthserviceapp.security;

import com.example.healthserviceapp.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class WebSecurity extends WebSecurityConfigurerAdapter {

    @Autowired
    private UsuarioService usuarioServicio;

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(usuarioServicio).passwordEncoder(new BCryptPasswordEncoder());
    }

    @Override
protected void configure(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests()
        .antMatchers(
            "/",
            "/login",
            "/registro",
            "/especialidades",
            "/especialidad/**",
            "/css/**",
            "/js/**",
            "/img/**",
            "/perfil/imagen/**",
            "/error",
            "/favicon.ico"
        ).permitAll()
        .antMatchers("/admin/**", "/obras_sociales/**")
            .hasRole("ADMIN")
        .antMatchers(
            "/consulta/paciente",
            "/consulta/consultas",
            "/consulta/diagnostico"
        ).hasRole("PROFESIONAL")
        .antMatchers("/profesional/**")
            .hasRole("PROFESIONAL")
        .antMatchers("/paciente/**", "/consulta/**")
            .hasRole("PACIENTE")
        .antMatchers("/perfil/**")
            .authenticated()
        .anyRequest()
            .authenticated()
        .and()
        .formLogin()
            .loginPage("/login")
            .loginProcessingUrl("/logincheck")
            .usernameParameter("email")
            .passwordParameter("password")
            .defaultSuccessUrl("/")
            .failureUrl("/login?error=true")
            .permitAll()
        .and()
        .logout()
            .logoutUrl("/logout")
            .invalidateHttpSession(true)
            .logoutSuccessUrl("/")
            .permitAll()
        .and()
        .csrf()
            .disable();
}
}
