package service;

import client.Session;
import dao.UserDAO;
import exception.AuthenticationException;
import model.*;
import security.PasswordHasher;
import security.RateLimiter;
import java.sql.SQLException;
import java.util.Optional;

public class AuthService {
    private final UserDAO userDAO;
    private final RateLimiter loginLimiter;
    public AuthService(UserDAO userDAO){this(userDAO,new RateLimiter(5,java.time.Duration.ofMinutes(5),java.time.Duration.ofMinutes(10)));}
    public AuthService(UserDAO userDAO,RateLimiter loginLimiter){this.userDAO=userDAO;this.loginLimiter=loginLimiter;}
    public User login(String email,String password)throws AuthenticationException{
        if(email==null||email.isBlank()||password==null||password.isBlank())throw new AuthenticationException("Email and password are required");
        if(!loginLimiter.allow(email))throw new AuthenticationException("Too many login attempts. Try again later.");
        try{
            Optional<AuthUser> record=userDAO.findForAuthentication(email);
            if(record.isEmpty()||!PasswordHasher.verify(password,record.get().passwordHash()))throw new AuthenticationException("Invalid email or password");
            loginLimiter.reset(email); User user=toUser(record.get()); Session.login(user); return user;
        }catch(SQLException|IllegalArgumentException e){throw new AuthenticationException("Authentication failed");}
    }
    public boolean registerEmail(String email)throws AuthenticationException{
        if(email==null||email.isBlank())throw new AuthenticationException("Email is required");
        try{return !userDAO.existsByEmail(email);}catch(SQLException e){throw new AuthenticationException("Unable to verify email");}
    }
    private User toUser(AuthUser u){return switch(u.role()){
        case STUDENT->new Student(u.id(),u.name(),u.email(),"",u.registerNumber(),u.department());
        case FACULTY->new FacultyStaff(u.id(),u.name(),u.email(),"",u.department());
        case MAINTENANCE,TECHNICIAN->new MaintenanceStaff(u.id(),u.name(),u.email(),"",u.specialization(),u.available());
        case ADMIN->new Admin(u.id(),u.name(),u.email(),"");};}
}
