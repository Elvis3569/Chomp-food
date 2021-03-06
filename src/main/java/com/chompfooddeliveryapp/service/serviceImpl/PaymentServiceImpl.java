package com.chompfooddeliveryapp.service.serviceImpl;

import com.chompfooddeliveryapp.dto.*;
import com.chompfooddeliveryapp.exception.BadRequestException;
import com.chompfooddeliveryapp.exception.UserNotAuthorizedException;
import com.chompfooddeliveryapp.model.carts.Cart;
import com.chompfooddeliveryapp.model.carts.CartItem;
import com.chompfooddeliveryapp.model.enums.OrderStatus;
import com.chompfooddeliveryapp.model.enums.PaymentMethod;
import com.chompfooddeliveryapp.model.enums.TransactionStatus;
import com.chompfooddeliveryapp.model.enums.TransactionType;
import com.chompfooddeliveryapp.model.orders.Order;
import com.chompfooddeliveryapp.model.users.User;
import com.chompfooddeliveryapp.model.wallets.Transaction;
import com.chompfooddeliveryapp.payload.PayStackResponse;
import com.chompfooddeliveryapp.payload.VerificationResponse;
import com.chompfooddeliveryapp.repository.*;
import com.chompfooddeliveryapp.security.service.UserDetailsServiceImpl;
import com.chompfooddeliveryapp.service.serviceInterfaces.PaymentService;
import com.chompfooddeliveryapp.exception.UserNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Objects;
import java.util.Optional;


@Service
public class PaymentServiceImpl implements PaymentService {
    private final PaystackServiceImpl payStackService;
    private final UserDetailsServiceImpl userDetailsService;
    private final WalletWithdrawServiceImpl withdrawService;
    private final TransactionRepository transactionRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserServiceImpl userService;
    private final AuthenticationManager authenticationManager;
    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;

    @Autowired
    public PaymentServiceImpl(PaystackServiceImpl payStackService, UserDetailsServiceImpl userDetailsService, WalletWithdrawServiceImpl withdrawService,
                              TransactionRepository transactionRepository, OrderRepository orderRepository, UserRepository userRepository, UserServiceImpl userService, AuthenticationManager authenticationManager, CartRepository cartRepository, CartItemRepository cartItemRepository, CartRepository cartRepository1) {

        this.payStackService = payStackService;
        this.userDetailsService = userDetailsService;
        this.withdrawService = withdrawService;
        this.transactionRepository = transactionRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.cartItemRepository = cartItemRepository;
        this.cartRepository = cartRepository;
    }

    @Override
    public Object processPayment(ProcessPaymentRequest request, Long userId, Long orderId)
    {
            checkLoginStatus(userId);


        Order userOrder = orderRepository.findOrderByIdAndUserId(orderId,userId)
                    .orElseThrow(() -> new BadRequestException("This order has not been made by the user"));
            if(Objects.equals(userOrder.getStatus(),OrderStatus.CONFIRMED)|| Objects.equals(userOrder.getStatus(),OrderStatus.DELIVERED)){
                throw new BadRequestException("This order has already been confirmed or delivered!");
            }

            //todo: Important!!! remove amount from ur request object and fetch it from Ifeanyi's Checkout
            if (request.getPaymentMethod().equals(PaymentMethod.PAYSTACK.name())){
                PayStackRequest requestDto = new PayStackRequest();
                requestDto.setAmount((long) userOrder.getAmount());



                return payStackService.initializePaystackTransaction(requestDto,userId, TransactionType.DEBIT);
            }
            if(request.getPaymentMethod().equals(PaymentMethod.EWALLET.name())){
                WithdrawalRequest walletWithdraw = new WithdrawalRequest();
                walletWithdraw.setBill((long) userOrder.getAmount());
                walletWithdraw.setUserId(userId);
                ResponseEntity<WithdrawalDto> output = withdrawService.walletWithdraw(walletWithdraw);
                if(Objects.equals(Objects.requireNonNull(output.getBody()).getMessageStatus(),"Withdrawal successful!")){
                    userOrder.setStatus(OrderStatus.CONFIRMED);
                    orderRepository.save(userOrder);

                    return output;
                }
                throw new BadRequestException("Withdrawal not successful");
            }
            else {
                throw new BadRequestException("Invalid payment method selected");
            }




    }

    @Override
    public VerificationResponse verifyPayStackPayment(VerifyTransactionDto transactionDto, Long userId, Long orderId)
            throws JsonProcessingException {
        PayStackResponse payStack =  payStackService.verifyPaystackTransaction(transactionDto);
        VerificationResponse response = new VerificationResponse();
        response.setStatus(payStack.getStatus());
        response.setMessage(payStack.getMessage());
        response.setAmount(payStack.getData().get("amount"));
        response.setPaymentDate(payStack.getData().get("paid_at"));
        if(!Objects.equals(payStack.getData().get("gateway_response"),"Successful")
                || !Objects.equals(payStack.getData().get("reference"),transactionDto.getTransactionReference())){
            throw new BadRequestException("Verification failed, Please contact your Bank");
        }
        Transaction transaction = transactionRepository
                .findById(transactionDto.getTransactionReference())
                .orElseThrow(() -> new BadRequestException("transaction doesn't exist"));
        transaction.setTransactionStatus(TransactionStatus.SUCCESSFUL);

        Order userOrder = orderRepository.findOrderByIdAndUserId(orderId,userId)
                .orElseThrow(() -> new BadRequestException("This order has not been made"));
        userOrder.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(userOrder);
        transactionRepository.save(transaction);

        //todo: change the status of the user's order to confirmed
        System.out.println(payStack.getData()+" Success");        return response;

    }

    @Override
    public void checkLoginStatus(Long userId){
        User user = userRepository.findUserById(userId)
                .orElseThrow(() -> new UserNotFoundException("User with ID: "+userId+" doesn't exist"));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(user.getEmail().equals(authentication.getName()))){
            throw new UserNotAuthorizedException("User Not logged in");
        }
    }
}
