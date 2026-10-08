package com.juanbarros.helpdesk.user;

import com.juanbarros.helpdesk.common.BusinessRuleException;
import com.juanbarros.helpdesk.common.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll(Sort.by("fullName")).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserSummary> findAgents() {
        return userRepository.findByRoleInOrderByFullNameAsc(List.of(Role.AGENT, Role.ADMIN))
                .stream().map(UserSummary::from).toList();
    }

    @Transactional
    public UserResponse updateRole(Long userId, Role newRole, User currentUser) {
        if (currentUser.getId().equals(userId)) {
            throw new BusinessRuleException("No podés cambiar tu propio rol");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        user.setRole(newRole);
        return UserResponse.from(user);
    }
}
