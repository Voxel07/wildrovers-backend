import React from 'react'
import './SignUp.css';
import { Formik, Field, Form } from 'formik';
import {Container, TextField, Button, } from "@material-ui/core";

import * as yup from 'yup';

const validationSchema = yup.object({
    firstName: yup.string().required().min(3).max(20, "Max Länge 20"),
    lastName: yup.string().required().min(3).max(20, "Max Länge 20"),
    nickName: yup.string().required().max(20, "Max Länge 20"),
    password: yup.string().required().min(5),
    passwordWdh: yup.string().required().oneOf([yup.ref('password'), null], 'Passwörter müssen übereinstimmen'),
    email: yup.string().required().email()
})

function SignUp() {

    return (
        <div>
            <Formik validateOnChange={true}
                initialValues={
                    {
                        firstName: '',
                        lastName: '',
                        nickName:'',
                        email:'',
                        password:'',
                        passwordWdh:''
                    }
                }
                validationSchema={validationSchema}
                onSubmit={
                    (data, { setSubmitting, resetForm }) => {
                        setSubmitting(true);
                        //Post From
                        console.log(data);
                        setSubmitting(false);
                        resetForm(true);
                        
                    }
                }

            >
                {
                    ({ values, errors, isSubmitting }) => (
                        <Container className="Form-Container" >
                            <div className="Form-Header">
                            <div className="Form-Header-Titel">
                               <div className= "Form-Header-Titel-Emblem"></div>
                               <div className= "Form-Header-Titel-Text"></div>
                            </div>
                            <div className="Form-Header-SubTitle">JOIN US</div>

                            </div>
                            <Form className="Form" >
                                <div className="Form-Field-TextField">
                                    <Field variant="outlined" label="Vorname" name="firstName" type="input" error={!!errors.firstName} helperText={errors.firstName} as={TextField} />
                                </div>

                                <div className="Form-Field-TextField">
                                    <Field variant="outlined" label="Nachname" name="lastName" type="input" error={!!errors.lastName} helperText={errors.lastName} as={TextField} />
                                </div>

                                <div className="Form-Field-TextField">
                                    <Field variant="outlined" label="Nickname" name="nickName" type="input" error={!!errors.nickName} helperText={errors.nickName} as={TextField} />
                                </div>
                                <div className="Form-Field-TextField">
                                    <Field variant="outlined" label="Email" name="email" type="email" error={!!errors.email} helperText={errors.email} as={TextField} />
                                </div>
                                <div className="Form-Field-TextField">
                                    <Field variant="outlined" label="Passwort" name="password" type="password" error={!!errors.password} helperText={errors.password} as={TextField}/>
                                     
                                </div>
                                <div className="Form-Field-TextField">
                                    <Field variant="outlined" label="Passwort wiederholen" name="passwordWdh" type="password" error={!!errors.passwordWdh} helperText={errors.passwordWdh} as={TextField} />
                                </div>
                                <div className="Form-Field-TextField">
                                    <Field variant="outlined"  name="geburtstag" type="date" error={!!errors.geburtstag} helperText={errors.geburtstag} as={TextField} />
                                </div>

                                <div>
                                    <Button disabled={isSubmitting || !errors} type='submit'> submit </Button></div>
                                {/* <pre> {JSON.stringify(values, null, 2)} </pre>
                                <pre> {JSON.stringify(errors, null, 2)} </pre> */}
                            </Form>
                        </Container>
                    )
                }
            </Formik>
        </div>
    );
}

export default SignUp